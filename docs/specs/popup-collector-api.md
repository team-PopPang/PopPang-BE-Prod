# 팝업 수집기 API

## 상태와 범위

IMPLEMENTED. 수집기의 DB 직접 등록을 API 호출로 전환한다. 기존 v1 등록 응답 변경과
신규 알림 대상 API를 `feature/popup-collector-api`에서 구현하고 로컬 검증했다. 운영 배포는 미실행이다.
FCM 발송은 수집기가 담당한다. 운영 주소 5건은 사용자가 추후 직접 수정한다.

## 등록 계약

`POST /api/v1/popup`의 요청 Body와 Header, 이미지 경로 저장 방식은 유지한다.
기존 `ApiResponse` 규칙으로 `data: { "popupUuid": "…", "created": true }`를 반환한다.
동일한 `instaPostId`가 있으면 팝업·이미지·추천 연결을 변경하지 않고 기존 UUID와
`created: false`를 반환한다. 이름이 달라도 게시물 ID만으로 중복을 판단한다.
기존에 허용된 null ID는 그대로 허용하며 중복 판별 대상에서 제외한다.

팝업과 연관 데이터는 한 트랜잭션으로 저장한다. 동시 등록은 기존
`popup.insta_post_id` 유니크 제약을 사용하며, 충돌한 트랜잭션이 롤백된 뒤 기존 UUID를
조회한다. 게시물 중복과 관계없는 무결성 오류는 성공으로 처리하지 않는다.

## 알림 대상 계약

`POST /api/v1/popup/alert-targets`에 `{ "popupUuids": ["…"] }`를 전달한다.
이 신규 경로에만 기존 `X-Worker-Api-Key` 인증을 적용한다. 서버 설정은
`internal.worker.api-key`를 사용하며 키 누락·오류는 HTTP 401, 오류 코드 5009다.
Bearer token이나 query parameter로 대체할 수 없다.

1. `is_deleted = 0 AND is_alerted = 1`인 사용자의 키워드를 조회한다.
2. 키워드가 팝업 `name` 또는 `caption_summary`에 포함되면 매칭한다.
   `%`, `_`도 일반 문자로 검색한다. 대소문자 구분은 기존 MySQL collation을 따른다.
   비어 있거나 공백뿐인 키워드는 제외한다.
3. 매칭된 `(user, popup)`의 `user_alert`가 없으면 기록한다. FCM 토큰이 없어도 기록한다.
4. FCM 토큰이 null 또는 공백인 사용자를 응답에서 제외하고 사용자별로 묶는다.

응답은 `ApiResponse<List<...>>`이며 각 항목은 `userUuid`, `fcmToken`, `keywords`,
`popups`다. 팝업 항목은 `popupUuid`, `name`, `region`을 포함한다. 키워드와 팝업은
중복을 제거하고 팝업 순서는 요청 UUID 순서를 따른다. 빈 요청 배열·매칭 없음은 `data: []`다.
알림 기록이 이미 있는 재요청도 매칭 대상은 반환한다. 푸시 발송 여부와 재시도는
수집기가 판단하며, 이 API가 FCM 발송의 정확히 한 번 실행을 보장하지 않는다.

배열 누락, null/공백 UUID는 HTTP 400(4316)이다. 존재하지 않는 UUID가 섞이면
HTTP 404(4301)로 전체 요청을 실패시키며 알림을 일부 저장하지 않는다.
중복 UUID는 첫 등장 위치를 유지해 한 번만 처리한다. 보통 0~3개라는 사용량 설명을
최대 3개 제한으로 해석하지 않는다.

## 동시성 및 배포

알림 조회·등록은 READ COMMITTED 트랜잭션에서 팝업 행을 ID 순서로 잠근 후 수행한다.
기존 v1/v2 단건 알림 등록도 같은 행 잠금을 사용해 API 간 중복 기록을 방지한다.
직접 DB에 쓰는 외부 프로그램은 이 잠금 규약을 따르지 않을 수 있으므로 수집기 전환 시
기존 직접 기록 경로를 중단해야 한다. 기존 중복 알림을 삭제하거나 DB DDL을 자동 실행하지 않는다.

배포 전 운영 `popup.insta_post_id`의 단일 컬럼 유니크 인덱스와 Worker Key 설정을 확인한다.
현재 엔티티에는 유니크 제약이 선언돼 있으나 이번 작업에서 운영 DB는 확인하지 않았다.
등록 응답에서 `created: true`인 UUID만 수집기가 알림 대상 요청으로 전달한다.
기존 v2 등록 API 계약은 변경하지 않는다.

## 검증 기준

- 신규·중복·동시 등록 결과, 이미지/추천 저장과 실패 시 전체 롤백
- 사용자 상태 필터, 이름/요약의 문자 포함 검색, 토큰 없는 사용자의 기록
- 사용자별 묶음, 중복 제거, 요청 순서, 기존 기록 재요청, 동시 알림 요청
- 신규 경로의 Worker Key 인증과 기존 v1/v2 인증·endpoint 목록 호환성
- 운영 데이터 수정과 배포는 로컬 자동 테스트 결과와 별도로 확인

검증 명령: `./gradlew clean test spotlessCheck --no-daemon`.
일회용 MySQL의 동시 등록·알림 저장·롤백 검증을 포함해 전체 테스트와 포맷 검사를 통과했다.

## 후속 수동 수정: 주소 5건

수정할 컬럼은 `popup.road_address`뿐이다. 아래 UUID의 현재 값을 먼저 조회하고
요청서의 기존 값과 일치하는 행에만 새 값을 적용한다. 이 작업에서는 운영 DB를 수정하지 않는다.

| popupUuid | 기존 값 | 변경할 값 |
|---|---|---|
| `9909b6ac-7fe8-11f1-9bec-9a9c0e700fea` | 전남광주통합특별 동구 독립로 268 | 광주 동구 독립로 268 |
| `95ae6071-80ad-11f1-9bec-9a9c0e700fea` | 전남광주통합특별 동구 독립로 268 | 광주 동구 독립로 268 |
| `b8be1f38-ad88-11f1-8bf3-6e4eca82e674` | 전남광주통합특별 동구 독립로 268 | 광주 동구 독립로 268 |
| `9936373e-aadd-11f1-8bf3-6e4eca82e674` | 전남광주통합특별 서구 무진대로 932 | 광주 서구 무진대로 932 |
| `8a8722c8-8d48-11f1-9bec-9a9c0e700fea` | 전남광주통합특별 무안군 삼향읍 남악로162번길 80 | 전남 무안군 삼향읍 남악로162번길 80 |

주소 정규화 기능 추가나 `region` 등 다른 컬럼의 일괄 변경은 요청 범위에 포함하지 않는다.

수동 실행 시 먼저 대상 5행을 조회·백업하고 기존 값과 일치하는지 확인한다.
아래 UPDATE는 UUID와 기존 주소를 함께 조건으로 사용한다. 같은 세션의 트랜잭션에서
각 변경 행 수가 3·1·1인지, 최종 주소가 위 표와 일치하는지 확인한 뒤 COMMIT한다.
행이 없거나 값이 달라졌다면 ROLLBACK하고 원인을 확인한다. 이미 수정된 행은 재수정하지 않는다.

```sql
START TRANSACTION;
SELECT uuid, road_address FROM popup WHERE uuid IN (
  '9909b6ac-7fe8-11f1-9bec-9a9c0e700fea',
  '95ae6071-80ad-11f1-9bec-9a9c0e700fea',
  'b8be1f38-ad88-11f1-8bf3-6e4eca82e674',
  '9936373e-aadd-11f1-8bf3-6e4eca82e674',
  '8a8722c8-8d48-11f1-9bec-9a9c0e700fea'
) FOR UPDATE;

UPDATE popup SET road_address = '광주 동구 독립로 268'
WHERE uuid IN (
  '9909b6ac-7fe8-11f1-9bec-9a9c0e700fea',
  '95ae6071-80ad-11f1-9bec-9a9c0e700fea',
  'b8be1f38-ad88-11f1-8bf3-6e4eca82e674'
) AND road_address = '전남광주통합특별 동구 독립로 268';
SELECT ROW_COUNT(); -- 기대값 3

UPDATE popup SET road_address = '광주 서구 무진대로 932'
WHERE uuid = '9936373e-aadd-11f1-8bf3-6e4eca82e674'
  AND road_address = '전남광주통합특별 서구 무진대로 932';
SELECT ROW_COUNT(); -- 기대값 1

UPDATE popup SET road_address = '전남 무안군 삼향읍 남악로162번길 80'
WHERE uuid = '8a8722c8-8d48-11f1-9bec-9a9c0e700fea'
  AND road_address = '전남광주통합특별 무안군 삼향읍 남악로162번길 80';
SELECT ROW_COUNT(); -- 기대값 1

-- 위 SELECT를 다시 실행해 최종 주소를 검증한다.
-- 모두 일치하면 COMMIT; 불일치하면 ROLLBACK; 중 하나를 실행한다.
```
