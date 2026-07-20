# 공개 Web 팝업 조회 API 설계

## 상태

IMPLEMENTED

## 목적

PopPang 웹 프론트가 인증이나 사용자 UUID 없이 팝업을 검색하고 홈 카드 목록을 필터링할 수
있도록 공개 Web 팝업 조회 계약을 제공한다. 검색은
`GET /api/v1/web/popup/search?q={검색어}`를 사용하고, 홈 필터는 기존
`GET /api/v1/web/popup/in-progress`의 선택적 Query parameter로 제공한다. 기존 비회원·회원
검색 및 `filtered/home` API 계약은 변경하지 않는다.

## 현재 동작

기존 비회원 검색 `GET /api/v1/popup/search`는 다음 의미를 가진다.

- 서비스에서 검색어의 앞뒤 공백을 제거한다.
- 이름(`name`) 또는 설명 요약(`captionSummary`)을 대소문자 구분 없이 부분 일치로 검색한다.
- `is_active = true`이고 `end_date >= CURRENT_DATE`인 팝업만 반환한다.
- 진행 중인 팝업과 모든 오픈 예정 팝업을 포함하고, 종료된 팝업은 제외한다.
- 명시적인 정렬은 없다.
- 빈 검색어는 빈 목록으로 처리한다.
- `%`, `_`는 LIKE 와일드카드로 해석된다.

현재 Web 목록 API는 `popup_image.sort_order = 0`인 이미지를 대표 이미지로 사용한다.
`Popup`에는 삭제·숨김·비공개·Web 노출 여부를 나타내는 별도 필드가 없으므로,
현재 공개 노출 여부를 판별할 수 있는 상태값은 `is_active`뿐이다.

## 결정

Web 검색 전용 Projection 쿼리를 추가한다. 이 쿼리는 검색, 공개 노출 필터, 대표 이미지
선택, 안정적인 정렬을 한 번의 DB 조회로 처리한다.

이 방식은 기존 검색 쿼리의 이름·설명 검색과 날짜·활성 조건을 유지하면서 Web 요구사항인
지역 검색과 카드 전용 응답을 추가한다. 기존 검색 Repository 메서드를 변경하거나 기존 API를
HTTP로 다시 호출하지 않는다.

대안으로 검토한 기존 검색 결과와 지역 검색 결과를 합치는 방식은 기존 쿼리를 직접
재사용할 수 있지만, 검색 두 번과 이미지 배치 조회 한 번이 필요하고 중복 제거와 정렬도
애플리케이션에서 수행해야 하므로 선택하지 않았다. 공통 검색 계층 리팩터링은 기존 회원·비회원
검색의 회귀 위험과 작업 범위가 커서 이번 범위에서 제외한다.

## HTTP 계약

```http
GET /api/v1/web/popup/search?q=성수
Accept: application/json
```

- 인증, 사용자 UUID, 쿠키, 세션, 요청 본문이 없다.
- Query parameter 이름은 `q`이며 필수 문자열이다.
- 페이지네이션, 정렬, 지역, 날짜 Query parameter를 추가하지 않는다.
- 성공 응답 Content-Type은 `application/json`이다.
- 검색 결과가 없으면 HTTP 200과 `data: []`를 반환한다.
- 기존 Web Controller의 `ApiResponse<List<...>>` 형식을 사용한다.

Controller 메서드는 `PopupWebController#getWebSearchPopupList`로 추가한다.

```java
@Operation(
    operationId = "getWebSearchPopupList",
    summary = "[WEB] 팝업 검색",
    description = "검색어를 이용해 웹에 공개된 팝업스토어 목록을 검색합니다.")
@GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
public ApiResponse<List<PopupWebSearchResponseDto>> getWebSearchPopupList(
    @Parameter(description = "검색어", required = true)
        @RequestParam(name = "q", required = false)
        String q)
```

`@RequestParam`은 누락된 값도 서비스의 동일한 검증 경로로 보내기 위해 런타임상
`required = false`로 받는다. OpenAPI 계약에서는 `@Parameter(required = true)`로 필수값을
명시한다.

Swagger 계약은 다음과 같다.

- Tag: `[WEB] [POPUP]`
- Summary: `[WEB] 팝업 검색`
- OperationId: `getWebSearchPopupList`
- 200 media type: `application/json`
- Response schema: `ApiResponseListPopupWebSearchResponseDto`

프로젝트의 OpenAPI에는 전역 `bearerAuth`가 설정되어 있지만 실제 Security 설정은
`anyRequest().permitAll()`이다. 기존 공개 Web API에도 별도 Swagger 보안 해제 표현이 없으므로
이번 작업에서 전역 OpenAPI 또는 Security 설정은 변경하지 않는다.

## 요청 검증과 오류

`PopupWebServiceImpl#getSearchPopupList`가 검색어를 검증한다.

1. `q == null`이면 유효하지 않은 검색어로 처리한다.
2. `q.trim()` 결과가 빈 문자열이면 유효하지 않은 검색어로 처리한다.
3. 유효한 검색어는 trim 결과를 Repository에 전달한다.

잘못된 검색어에는 신규 `ErrorCode.INVALID_POPUP_SEARCH_QUERY`를 사용한다.

```java
INVALID_POPUP_SEARCH_QUERY(HttpStatus.BAD_REQUEST, 4313, "검색어는 필수입니다.")
```

따라서 `q` 누락, `q=`, 공백만 있는 `q`는 HTTP 400과 기존 `ApiResponse.error(...)`
형식으로 응답한다. 검색어 최대 길이에 대한 기존 정책이 없으므로 제한을 추가하지 않는다.

## 검색 및 공개 노출 조건

검색어는 다음 필드와 대소문자 구분 없이 부분 일치한다.

- 팝업 이름 `p.name`
- 지역 `p.region`
- 설명 요약 `p.caption_summary`

주소, 도로명 주소, 전체 캡션, 추천 키워드는 검색하지 않는다. 기존 정책과 동일하게 `%`, `_`는
이스케이프하지 않으며 LIKE 와일드카드로 해석한다.

공개 노출과 유효 식별자 조건은 다음과 같다.

```sql
p.is_active = 1
AND p.end_date >= CURRENT_DATE
AND p.uuid IS NOT NULL
AND TRIM(p.uuid) <> ''
AND p.name IS NOT NULL
AND TRIM(p.name) <> ''
```

날짜별 포함 여부는 다음과 같다.

| 상태 | 조건 | 포함 여부 |
|---|---|---|
| 진행 중 | `start_date <= CURRENT_DATE <= end_date` | 포함 |
| 오늘 종료 | `end_date = CURRENT_DATE` | 포함 |
| 오픈 예정 | `start_date > CURRENT_DATE` | 포함 |
| 종료 | `end_date < CURRENT_DATE` | 제외 |
| 비활성 | `is_active = 0` | 제외 |

오픈 예정 팝업에는 상한 날짜를 두지 않는다. `/upcoming` API의 10일 범위는 검색 API에
적용하지 않는다.

## 대표 이미지와 Projection

신규 `PopupWebSearchRow` Projection은 다음 값을 제공한다.

```java
String getPopupUuid();
String getPopupName();
String getThumbnailUrl();
String getRegion();
LocalDate getStartDate();
LocalDate getEndDate();
```

대표 이미지는 기존 Web 목록 API와 동일하게 `popup_image.sort_order = 0`으로 선택한다.
이미지가 없는 팝업도 검색 결과에 포함되어야 하므로 현재 `in-progress` Web API처럼
`LEFT JOIN`을 사용한다. 이미지가 없으면 `thumbnailUrl`은 `null`이며 URL을 새로 조합하거나
변환하지 않고 저장된 `image_url`을 그대로 반환한다.

## 정렬

기존 검색에는 명시적인 정렬이 없으므로 신규 Web 검색에는 다음 안정적인 정렬을 적용한다.

1. 이름 완전 일치
2. 이름 시작 일치
3. 이름 부분 일치
4. 지역 부분 일치
5. 설명 요약 부분 일치
6. 같은 관련도에서는 `start_date DESC`
7. 그래도 같으면 `uuid ASC`

정렬은 Repository 쿼리의 `CASE`와 보조 정렬 조건으로 처리하며 서비스는 Repository 순서를
그대로 보존한다.

## 응답 DTO

신규 `PopupWebSearchResponseDto`는 기존 Web 카드 DTO와 동일한 여섯 필드를 가진다.

```java
private String popupUuid;
private String name;
private String thumbnailUrl;
private String region;
private LocalDate startDate;
private LocalDate endDate;
```

`popupUuid`와 `name`의 null·빈 문자열·공백 문자열은 Repository 조건에서 제외한다.
나머지 필드는 `null`일 수 있다.

성공 응답 예시는 다음과 같다.

```json
{
  "success": true,
  "code": 0,
  "message": "요청 성공!",
  "data": [
    {
      "popupUuid": "7ed187ad-4ff9-11f1-8ba8-46b388519c93",
      "name": "성수 캐릭터 팝업",
      "thumbnailUrl": "/images/example/popup-thumbnail.jpg",
      "region": "서울 성수",
      "startDate": "2026-07-01",
      "endDate": "2026-07-31"
    }
  ]
}
```

## 애플리케이션 구조와 데이터 흐름

```text
GET /api/v1/web/popup/search?q=성수
  → PopupWebController#getWebSearchPopupList
  → PopupWebService#getSearchPopupList
  → PopupWebServiceImpl: null/blank 검사 및 trim
  → PopupRepository#searchWebActiveWithThumbnail
  → PopupWebSearchRow 목록
  → PopupWebSearchResponseDto 목록
  → ApiResponse.ok(...)
```

수정·추가할 주요 구성요소는 다음과 같다.

- `PopupWebController`: 신규 GET endpoint와 OpenAPI 계약
- `PopupWebService`, `PopupWebServiceImpl`: 검색어 검증 및 Projection-to-DTO 변환
- `PopupRepository`: Web 전용 단일 검색 쿼리
- `PopupWebSearchRow`: Web 검색 Projection
- `PopupWebSearchResponseDto`: Web 검색 카드 응답
- `ErrorCode`: 빈 검색어용 400 오류

## 테스트 전략

구현은 테스트 우선으로 진행한다.

### Controller/API 테스트

- Authorization 헤더 없이 HTTP 200을 반환한다.
- `q`를 서비스에 전달한다.
- 응답 Content-Type은 `application/json`이다.
- 성공 응답이 공통 `ApiResponse`와 여섯 카드 필드를 포함한다.
- 결과가 없으면 `data: []`이다.
- `/web/popup/search`가 `{popupUuid}` 상세 endpoint가 아닌 검색 endpoint로 연결된다.
- 기존 상세 endpoint는 계속 상세 서비스 메서드로 연결된다.
- `q` 누락, 빈 문자열, 공백 문자열은 HTTP 400 공통 오류 응답이다.

### Service 테스트

- 검색어 앞뒤 공백을 제거한 값을 Repository에 전달한다.
- Projection의 여섯 필드를 DTO로 변환하고 Repository 순서를 보존한다.
- Repository 결과가 없으면 빈 목록을 반환한다.
- null·빈 문자열·공백 문자열은 `INVALID_POPUP_SEARCH_QUERY`를 발생시킨다.

### Repository 계약 테스트

- 이름·지역·설명 요약을 대소문자 무시 부분 일치로 검색한다.
- 활성 상태와 종료일 조건을 적용한다.
- UUID와 이름의 null·blank 값을 제외한다.
- `sort_order = 0` 이미지를 `LEFT JOIN`한다.
- 관련도, 시작일, UUID의 안정적인 정렬을 적용한다.
- 기존 `searchActivatedByKeyword` 쿼리 문자열과 의미는 변경하지 않는다.

### OpenAPI 테스트

- 신규 path, 필수 `q`, 태그, summary, operationId를 검증한다.
- 200 응답의 media type과 wrapper schema를 검증한다.
- DTO 필드명과 날짜 format을 검증한다.
- 기존 OpenAPI 기준선과 비교하여 신규 path와 schema 외의 변경이 없는지 확인한다.

## 진행 중 Web 홈 필터 계약

기존 경로를 다음과 같이 확장한다. `home/filtered`, `filtered/home` 등 별도 Web 경로는
추가하지 않는다.

```http
GET /api/v1/web/popup/in-progress?region=서울&district=성동구&sort=NEWEST
Accept: application/json
```

- `region`, `district`, `sort`는 모두 선택적 Query parameter다.
- `sort`는 `MOST_FAVORITED`, `MOST_VIEWED`, `NEWEST`, `CLOSING_SOON`만 허용한다.
- `district`를 생략하거나 `전체`로 보내면 해당 지역 전체를 조회한다.
- 유효한 `district`만 있고 `region`이 없으면
  `REGION_REQUIRED_FOR_DISTRICT`로 HTTP 400을 반환한다.
- 지원하지 않는 `sort`는 기존 `INVALID_SORT_STANDARD`로 HTTP 400을 반환한다.
- 필터 호출에서 `sort`를 생략하면 `CLOSING_SOON`을 기본 정렬로 사용한다.
- 세 Query parameter가 모두 없으면 기존 `findInProgressActiveWithThumbnail` 조회와 정렬을
  그대로 사용한다.
- 필터를 적용한 결과는 `is_active = true`이고
  `start_date <= CURRENT_DATE <= end_date`인 팝업만 포함한다.
- 인증, 사용자 UUID, 쿠키, 세션 및 사용자별 데이터는 사용하지 않는다.

응답은 기존 Web 목록과 같은 `ApiResponse<List<PopupWebInProgressResponseDto>>`다. 카드에는
`popupUuid`, `name`, `thumbnailUrl`, `region`, `startDate`, `endDate`만 포함하고, 대표 이미지는
`sort_order = 0`인 첫 이미지를 사용한다. 이미지가 없으면 `thumbnailUrl`은 `null`이다.

지역 정규화와 네 정렬별 Repository 선택은 공통 application service에서 한 번만 수행한다.
기존 비회원 `GET /api/v1/popup/filtered/home`과 회원
`GET /api/v1/users/{userUuid}/popups/filtered/home`도 같은 공통 조회 service에 위임하되 URL,
parameter 이름, 응답 DTO 및 사용자별 데이터 조립은 그대로 유지한다.

```text
GET /api/v1/web/popup/in-progress?region=서울&district=전체&sort=MOST_VIEWED
  → PopupWebController#getWebInProgressPopupList
  → PopupWebService#getInProgressPopupList
  → Web 요청 조합 및 sort 검증
  → 공통 홈 필터 service: 지역 정규화 및 기존 정렬별 Repository 조회
  → Web 카드 mapper: 대표 이미지 배치 조회 및 경량 DTO 조립
  → ApiResponse.ok(...)
```

다음 회귀 및 계약을 자동 테스트한다.

- 무파라미터 호출이 기존 전용 조회와 Web envelope/DTO를 유지한다.
- 지역 전체, 지역과 구, `district=전체`를 정규화해 조회한다.
- 네 정렬 기준이 각각 기존 Repository 조회를 사용한다.
- 잘못된 정렬값과 `district` 단독 요청이 Web 오류 envelope의 HTTP 400을 반환한다.
- 모든 정렬 쿼리가 비활성, 종료 및 오픈 예정 팝업을 제외하는 조건을 유지한다.
- OpenAPI가 세 선택 parameter, 네 enum 값, 정렬 의미, 진행 중 조건 및
  `ApiResponseListPopupWebInProgressResponseDto` 응답 schema를 노출한다.

### 전체 검증

```bash
./gradlew test build spotlessCheck --rerun-tasks
```

DB·Redis·private 설정 때문에 전체 명령이 실패하면 실패 원인과 성공한 범위를 그대로 보고한다.

## 범위 밖

- 기존 비회원·회원 검색 API 변경
- 기존 Web API 응답 변경
- 페이지네이션, 자동완성, 검색 기록, 인기 검색어, 분석 이벤트
- Elasticsearch 또는 다른 dependency 도입
- DB schema, 환경변수, 캐시 변경
- 프론트엔드 변경
- push, main merge, 배포
