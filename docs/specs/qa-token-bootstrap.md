# 운영 QA 토큰 부트스트랩

## Status

IMPLEMENTED

## Goal

운영 서버 접속 권한이 없는 팀원이 v2 Swagger에서 고정 테스트 계정의 Access Token과
Refresh Token을 발급받아 MEMBER 및 ADMIN API를 검증할 수 있게 한다.

## Scope

- 운영 환경에서 단일 QA 토큰 발급 API를 제공한다.
- 하나의 공통 QA API Key로 MEMBER와 ADMIN 테스트 계정 발급을 모두 허용한다.
- 요청자는 `account=MEMBER|ADMIN` 쿼리 파라미터로 사전에 설정된 계정을 선택한다.
- 기존 v2 JWT 발급 및 Refresh Token 저장 방식을 그대로 사용한다.
- v2 Swagger에 QA API Key 인증 항목과 토큰 발급 API를 노출한다.

## Non-goals

- 임의의 `uid`, `userUuid`, DB id를 입력받아 토큰을 발급하지 않는다.
- 소셜 로그인 자체를 대체하거나 검증하지 않는다.
- 테스트 계정을 애플리케이션이 자동 생성하거나 수정하지 않는다.
- QA API Key를 코드, OpenAPI 문서, 로그 또는 URL에 저장하지 않는다.

## API contract

### 토큰 발급

- Method: `POST`
- Path: `/api/v2/test-auth/token`
- Header: `X-QA-Api-Key: <공통 QA 비밀키>`
- Query parameter: `account=MEMBER|ADMIN`
- Request body: 없음
- Success: `200 OK`, `ApiResponse<V2TokenResponseDto>`
- Response headers: `Cache-Control: no-store`, `Pragma: no-cache`

`account`는 대소문자를 구분하지 않고 처리하되 Swagger에는 `MEMBER`, `ADMIN`만 선택지로
노출한다. 다른 값, 누락 또는 빈 값은 잘못된 요청으로 거부한다.

## Authentication and authorization

- 토큰 발급 API는 일반 Bearer JWT가 아닌 전용 `X-QA-Api-Key`로만 인증한다.
- QA API Key는 MEMBER와 ADMIN에 공통으로 사용하며 최소 32자 이상이어야 한다.
- 서버는 제공된 키와 설정값을 상수 시간 비교한다.
- 키가 없거나 비어 있거나 일치하지 않으면 `401 Unauthorized`로 거부한다.
- Swagger v2 문서에 `qaApiKeyAuth` 헤더 인증을 추가하지만 실제 키 값은 포함하지 않는다.
- QA API Key를 아는 팀원은 MEMBER와 ADMIN 토큰을 모두 발급할 수 있다.
- QA API Key와 테스트 계정 UUID는 운영 비밀 설정으로 관리하고 저장소에 커밋하지 않는다.

## Flow and data

1. `account`를 MEMBER 또는 ADMIN으로 해석한다.
2. 운영 설정의 `qa.auth.member-user-uuid` 또는 `qa.auth.admin-user-uuid`에서 고정 UUID를
   선택한다.
3. 해당 사용자가 DB에 존재하고, 탈퇴하지 않았고, 가입 상태가 `COMPLETED`이며, 요청한
   역할과 실제 역할이 일치하는지 확인한다.
4. 기존 v2 토큰 서비스를 사용하여 Access Token과 Refresh Token을 발급한다.
5. Refresh Token의 원문이 아닌 기존 fingerprint 레코드를 운영 Redis에 저장한다.
6. 발급 결과를 캐시 금지 헤더와 함께 반환한다.

기존 v2 인증은 사용자별 최신 Refresh 세션 하나만 유지한다. 같은 테스트 계정의 토큰을
다시 발급하면 이전 Refresh Token은 더 이상 최신 토큰이 아니므로 사용할 수 없다.
Access Token의 권한은 이후 요청 시 DB의 현재 역할로 결정된다.

## Errors and edge cases

- QA API Key 누락·불일치: `401 INVALID_QA_API_KEY`
- `account` 누락·지원하지 않는 값: `400 INVALID_QA_ACCOUNT`
- 설정 UUID 누락, 사용자 없음, 탈퇴·가입 미완료, 역할 불일치: `503 QA_ACCOUNT_NOT_READY`
- Redis 등 인증 저장소 장애: 기존 `503 AUTH_STORE_UNAVAILABLE`
- 오류 응답과 애플리케이션 로그에는 제공된 키, 설정 키, 발급 토큰을 포함하지 않는다.

## Compatibility and rollout

- 기존 v1/v2 API의 요청과 응답 계약은 변경하지 않는다.
- `/api/v2/test-auth/token`에만 적용되는 전용 보안 경계를 v2 일반 JWT 경계보다 먼저
  평가한다.
- 배포 전에 운영 DB에 활성화된 `MEMBER`, `ADMIN` 계정을 각각 준비한다.
- 배포 전에 운영 비밀 설정에 공통 QA API Key와 두 계정 UUID를 추가한다. 설정이
  유효하지 않으면 애플리케이션 시작을 실패시켜 보호되지 않은 상태로 실행되지 않게 한다.
- 환경변수로 주입할 때의 이름은 `QA_AUTH_API_KEY`, `QA_AUTH_MEMBER_USER_UUID`,
  `QA_AUTH_ADMIN_USER_UUID`다.
- 키가 노출된 것으로 의심되면 운영 설정의 QA API Key를 교체하고 재배포한다.

## Acceptance criteria

- 올바른 공통 키와 `MEMBER`로 MEMBER 테스트 계정의 ATK·RTK를 발급한다.
- 올바른 공통 키와 `ADMIN`으로 ADMIN 테스트 계정의 ATK·RTK를 발급한다.
- 키가 없거나 잘못되면 서비스와 토큰 발급 로직을 호출하지 않고 401을 반환한다.
- 임의 사용자 식별자를 요청으로 전달할 수 없다.
- 설정된 계정의 상태 또는 역할이 계약과 다르면 토큰을 발급하지 않는다.
- 응답은 토큰 캐싱을 금지한다.
- v2 OpenAPI는 해당 API에 `qaApiKeyAuth`만 요구하고 비밀값을 포함하지 않는다.

## Open questions

없음.
