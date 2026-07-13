# OpenAPI Media Type 정합성 개선 설계

## 목표

Spring Boot 3.5.6과 springdoc-openapi 2.7.0으로 생성되는 OpenAPI 문서의 response media type을 실제 Spring MVC 응답과 일치시킨다. API endpoint, response DTO, payload, status code, 인증·인가, 비즈니스 로직은 변경하지 않는다.

`main` merge, push, 배포는 이 작업 범위에 포함하지 않는다.

## 확인된 현상과 근본 원인

현재 공개 OpenAPI JSON에서 body가 있는 200 response 44개가 모두 `*/*`로 생성된다. 네 web popup GET도 동일하며 random과 detail의 schema 참조는 각각 다음과 같다.

- random: `#/components/schemas/ApiResponseListPopupWebRandomResponseDto`
- detail: `#/components/schemas/ApiResponsePopupWebDetailResponseDto`

실제 공개 `GET /api/v1/web/popup/random` 응답은 HTTP 200과 `Content-Type: application/json`을 반환한다.

컨트롤러의 `@RequestMapping`, `@GetMapping` 등에 `produces`가 선언되어 있지 않다. Springdoc 2.7.0은 Spring MVC의 비어 있는 produces 조건을 받으면 `SpringDocConfigProperties.defaultProducesMediaType`의 기본값인 `*/*`를 사용한다. 따라서 Jackson message converter가 실제로 선택하는 JSON 응답과 OpenAPI 선언이 달라진다.

공통 `OpenApiConfig`에는 media type customizer가 없고, 별도 message converter나 content negotiation 설정도 없다. `ResponseEntity<?>`, `ResponseEntity<Object>`, `Object` 반환도 발견되지 않았다.

## 선택한 접근

JSON body를 반환하는 Spring MVC mapping에 `produces = MediaType.APPLICATION_JSON_VALUE`를 명시한다.

- 모든 endpoint가 JSON body를 반환하는 컨트롤러는 클래스 수준 `@RequestMapping`에 선언한다.
- JSON body와 body 없는 response가 섞인 컨트롤러는 JSON body를 반환하는 메서드에만 선언한다.
- `ResponseEntity<Void>` endpoint에는 produces를 추가하지 않고 OpenAPI response content도 생성하지 않는다.
- 기존 multipart endpoint의 `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`는 유지한다. multipart endpoint가 JSON body를 반환하는 경우에만 produces를 별도로 명시한다.
- 현재 JSON request body 12개는 이미 `application/json`, multipart request body 2개는 이미 `multipart/form-data`로 생성되므로 불필요한 consumes 변경을 하지 않는다.
- 전역 `springdoc.default-produces-media-type` 변경이나 `OpenApiCustomizer`를 통한 `*/*` 치환은 사용하지 않는다.

이 방식은 정상 JSON 요청의 body, status와 `Content-Type`을 바꾸지 않고, 이미 JSON인 런타임 계약을 mapping과 OpenAPI에 명시한다. `Accept: application/json` 또는 `Accept: */*`인 기존 클라이언트 동작은 유지된다.

## 특수 media type 감사 결과

현재 OpenAPI에 포함된 controller endpoint에는 binary download, image response, SSE, streaming, redirect response가 없다. `/submissionImages` 정적 파일 경로는 controller/OpenAPI endpoint가 아니다.

두 multipart endpoint는 request 형식만 `multipart/form-data`이며 다음과 같다.

- `POST /api/v1/popup-submissions`
- `PUT /api/v1/admin/popup-submissions/{popupSubmissionId}`

첫 endpoint는 body 없는 200 response이고, 두 번째 endpoint는 JSON response DTO를 반환한다. JSON이 아닌 response를 `application/json`으로 바꾸는 작업은 없다.

## 테스트 설계

DB, Redis, 외부 인증 provider를 사용하지 않는 최소 Spring MVC/Springdoc 테스트 context에서 production controller mapping으로 `/v3/api-docs`를 생성한다.

수정 전에 다음 조건을 검사하는 regression test를 추가하고 현재 `*/*` 때문에 실패하는 RED 결과를 확보한다.

1. 모든 response content에 `*/*`가 없어야 한다.
2. response body가 있는 현재 controller operation은 `application/json`을 선언해야 한다.
3. body 없는 response에는 content를 강제하지 않는다.
4. JSON request body는 `application/json`, 두 multipart request body는 `multipart/form-data`를 유지해야 한다.
5. 네 web popup GET의 200 response는 `application/json`이어야 한다.
6. random과 detail의 200 schema `$ref`는 기존 이름을 유지해야 한다.

같은 테스트 context의 MockMvc로 네 web popup GET을 호출해 실제 HTTP response가 JSON 계열 Content-Type인지 확인한다. 서비스는 mock으로 대체하므로 DB 조회나 조회수 변경이 발생하지 않는다.

수정 후 같은 regression test를 실행해 GREEN 결과를 확보하고, 전체 `test`, `build`, `spotlessCheck`를 실행한다. 생성된 로컬 OpenAPI JSON을 다시 집계해 response `*/*` 잔여 목록과 request/response media type 분포를 기록한다.

## 오류 및 계약 보존

예외 처리, `ErrorCode`, `GlobalExceptionHandler`는 변경하지 않는다. controller method signature, DTO generic type, Swagger schema, status code, URL, security annotation도 변경하지 않는다.

검증에서는 수정 전후 OpenAPI의 path, method, status, schema `$ref`를 비교한다. 의도된 media type 차이 외의 계약 변경이 발견되면 구현 완료로 판단하지 않는다.

## 완료 조건

- 네 web popup GET의 200 response media type이 `application/json`이다.
- random/detail schema `$ref`가 그대로다.
- 모든 문서화된 JSON response에서 `*/*`가 제거된다.
- body 없는 response에는 content가 없다.
- multipart request media type이 유지된다.
- 실제 MockMvc JSON response의 Content-Type이 JSON 계열이다.
- 전체 test, build, Spotless 검사가 통과한다.
- main merge, push, 배포를 수행하지 않는다.
