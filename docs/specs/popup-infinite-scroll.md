# 팝업 무한 스크롤 목록

## Status

IMPLEMENTED

## Goal

iOS 앱이 팝업 목록 셀에 필요한 최소 정보만 15개씩 조회하고, 사용자가 셀을 선택하면
기존 회원 팝업 상세 API로 전체 정보를 별도 조회할 수 있도록 한다.

## Scope

- 회원용 v1 팝업 경량 목록 API를 추가한다.
- 숫자 커서를 이용해 최신 등록 팝업부터 15개씩 조회한다.
- 목록 항목은 팝업 UUID, 썸네일, 지역, 제목, 기간, 사용자 좋아요 여부만 반환한다.
- 기존 회원 팝업 상세 API를 그대로 사용한다.

## Non-goals

- 새로운 상세 API 추가 또는 기존 상세 응답 변경
- 새로운 Swagger 태그 추가
- 전체 개수, 전체 페이지 수 또는 페이지 번호 제공
- 페이지 크기, 필터 또는 정렬 기준을 클라이언트가 지정하는 기능
- 커서 암호화, 서명, Base64 인코딩 또는 복합 커서
- v2 JWT 계약으로의 변경

## API contract

### 경량 목록

```http
GET /api/v1/users/{userUuid}/popups/scroll?cursor={cursor}
Accept: application/json
```

- `userUuid`는 필수 path parameter다.
- `cursor`는 선택적 Long query parameter다.
- 최초 요청에서는 `cursor`를 보내지 않는다.
- 다음 요청부터 직전 응답의 `nextCursor`를 그대로 전달한다.
- 요청 본문은 없다.
- 페이지 크기는 서버에서 15개로 고정한다.

성공 응답은 별도 공통 envelope 없이 기존 회원 팝업 컨트롤러와 동일하게 DTO를 직접
반환한다.

```json
{
  "items": [
    {
      "popupUuid": "7ed187ad-4ff9-11f1-8ba8-46b388519c93",
      "thumbnailUrl": "/images/example/thumbnail.jpg",
      "region": "서울",
      "name": "성수 캐릭터 팝업",
      "startDate": "2026-08-01",
      "endDate": "2026-08-15",
      "isFavorited": true
    }
  ],
  "nextCursor": 108,
  "hasNext": true
}
```

- `items`는 최대 15개다.
- `nextCursor`는 다음 데이터가 있을 때 현재 응답의 마지막 팝업 내부 ID다.
- 마지막 페이지 또는 빈 결과에서는 `nextCursor`가 `null`이고 `hasNext`가 `false`다.
- 썸네일은 `popup_image.sort_order = 0`인 이미지 중 ID가 가장 작은 이미지다.
- 대표 이미지가 없으면 `thumbnailUrl`은 `null`이다.

### 기존 상세 조회

```http
GET /api/v1/users/{userUuid}/popups/{popupUuid}
Accept: application/json
```

목록 항목의 `popupUuid`를 사용하며 기존 `PopupUserResponseDto` 계약을 변경하지 않는다.
조회수 증가는 기존 `POST /api/v1/popup/{popupUuid}/view`, 연관 팝업은 기존
`GET /api/v1/users/{userUuid}/popups/{popupUuid}/related`를 계속 별도로 사용한다.

## Authentication and authorization

기존 v1 회원 팝업 API와 동일하게 URL의 `userUuid`로 사용자를 조회한다. 현재 v1 보안
정책은 해당 경로를 공개하므로 JWT 인증 또는 새로운 권한 정책을 추가하지 않는다.

## Flow and data

1. `cursor`가 없으면 `id DESC` 기준 첫 페이지를 조회한다.
2. `cursor`가 있으면 `id < cursor`인 팝업만 조회한다.
3. `is_active = true`이고 `end_date >= 오늘`인 팝업만 포함한다.
4. 서버는 최대 15개와 다음 데이터 존재 여부를 조회한다.
5. 현재 페이지 팝업 ID에 대해서만 대표 이미지와 사용자 좋아요 여부를 배치 조회한다.
6. 다음 데이터가 있으면 마지막 항목의 ID를 `nextCursor`로 반환한다.

## Errors and edge cases

- 존재하지 않는 `userUuid`는 기존 `USER_NOT_FOUND` 오류를 반환한다.
- 숫자로 변환할 수 없는 `cursor`는 Spring 요청 변환 오류로 HTTP 400을 반환한다.
- 존재하지 않거나 범위를 벗어난 숫자 커서는 빈 `items`, `nextCursor: null`,
  `hasNext: false`로 처리한다.
- 조회 결과가 비어 있어도 HTTP 200을 반환한다.

## Compatibility and rollout

- 기존 `GET /api/v1/users/{userUuid}/popups` 전체 목록 계약을 변경하지 않는다.
- 기존 상세, 검색, 필터, 추천 API 계약을 변경하지 않는다.
- 신규 endpoint는 기존 `[POPUP-USER] 회원` Swagger 태그에 포함되며 새 태그를 만들지 않는다.

## Acceptance criteria

- 최초 요청은 커서 없이 최신 팝업 최대 15개를 반환한다.
- 후속 요청은 `nextCursor`보다 작은 ID의 팝업을 중복 없이 반환한다.
- 16번째 조회 결과 존재 여부에 따라 `hasNext`가 결정된다.
- 마지막 페이지와 빈 결과는 `nextCursor: null`, `hasNext: false`다.
- 비활성 또는 종료된 팝업은 반환되지 않는다.
- 각 목록 항목은 요구된 일곱 필드만 가진다.
- 대표 이미지와 좋아요 여부 조회는 페이지 단위 배치 조회로 수행한다.
- 기존 상세 API와 v1 endpoint 호환성 테스트가 유지된다.
