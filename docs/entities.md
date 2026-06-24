# PopPang 엔티티 정의서

> 위치: `src/main/java/com/poppang/be`
> 최종 갱신: 2026-06-23

전체 `@Entity` 15개와 공통 `BaseEntity` 1개, 총 16개 문서화 대상이다.
모든 엔티티가 `BaseEntity`를 상속하지는 않으므로, 감사(audit) 필드 보유 여부는 각 엔티티의 상속 여부를 기준으로 확인한다.

---

## 목차

- [공통](#공통)
- [users 도메인](#users-도메인)
- [popup 도메인](#popup-도메인)
- [favorite 도메인](#favorite-도메인)
- [keyword 도메인](#keyword-도메인)
- [alert 도메인](#alert-도메인)
- [recommend 도메인](#recommend-도메인)
- [Enum 타입 정리](#enum-타입-정리)
- [제약 및 관계 전략](#제약-및-관계-전략)
- [엔티티 관계 개요](#엔티티-관계-개요)

---

## 공통

### BaseEntity `(@MappedSuperclass)`

JPA Auditing 기반 공통 필드. `@EntityListeners(AuditingEntityListener.class)`를 사용한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| createdAt | LocalDateTime | created_at | `@CreatedDate`, `nullable = false`, `updatable = false`, `@Temporal(TIMESTAMP)` |
| updatedAt | LocalDateTime | updated_at | `@LastModifiedDate`, `@Temporal(TIMESTAMP)` |

---

## users 도메인

### Users -> `users`

사용자 핵심 엔티티. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY, `nullable = false`, `updatable = false` |
| uid | String | uid | `UNIQUE`, len 255 |
| uuid | String | uuid | `NOT NULL`, len 36 |
| provider | Provider | provider | ENUM(STRING), len 20 |
| email | String | email | len 255 |
| nickname | String | nickname | `UNIQUE`, len 255 |
| role | Role | role | ENUM(STRING), len 20 |
| alerted | boolean | is_alerted | 코드상 nullable 허용, Java 기본값 `false` |
| fcmToken | String | fcm_token | len 255 |
| deleted | boolean | is_deleted | 코드상 nullable 허용, Java 기본값 `false` |

- 메서드: `ensureUuid()` - 저장 전 `uuid`가 없으면 UUID 자동 생성
- 메서드: `completeSignup(signupRequestDto)` - 이메일, 닉네임, 알림 동의, FCM 토큰 갱신
- 메서드: `changeNickname(changeNicknameRequestDto)`, `softDelete()`, `restore()`, `updateFcmToken(fcmToken)`, `updateAlerted(alerted)`

---

## popup 도메인

### Popup -> `popup`

팝업 스토어 본문 엔티티. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| uuid | String | uuid | `NOT NULL`, len 36 |
| name | String | name | `NOT NULL`, len 50 |
| startDate | LocalDate | start_date | `NOT NULL` |
| endDate | LocalDate | end_date | `NOT NULL` |
| openTime | LocalTime | open_time | |
| closeTime | LocalTime | close_time | |
| address | String | address | `NOT NULL`, len 255 |
| roadAddress | String | road_address | len 255 |
| region | String | region | `NOT NULL`, len 100 |
| latitude | Double | latitude | |
| longitude | Double | longitude | |
| geocodingQuery | String | geocoding_query | |
| instaPostId | String | insta_post_id | `NOT NULL`, `UNIQUE`, len 255 |
| instaPostUrl | String | insta_post_url | `NOT NULL`, len 255 |
| captionSummary | String | caption_summary | `NOT NULL`, TEXT |
| caption | String | caption | `NOT NULL`, TEXT |
| mediaType | MediaType | media_type | ENUM(STRING) |
| activated | boolean | is_active | `NOT NULL` |

- 메서드: `ensureUuid()` - 저장 전 `uuid`가 없으면 UUID 자동 생성
- 메서드: `deactivate()` - `activated`를 `false`로 변경

### PopupImage -> `popup_image`

팝업 이미지. 여러 이미지가 하나의 `Popup`에 연결된다. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| popup | Popup | popup_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| imageUrl | String | image_url | `NOT NULL`, len 1000 |
| sortOrder | int | sort_order | `NOT NULL` |

### PopupRecommend -> `popup_recommend`

`Popup`과 `Recommend` 추천 카테고리의 조인 엔티티. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| popup | Popup | popup_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| recommend | Recommend | recommend_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |

### PopupCountBoost -> `popup_count_boost`

운영자 가산 조회수/찜 수. `Popup`과 공유 PK 기반 1:1 관계를 가진다. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| popupId | Long | popup_id | PK, 공유 PK |
| popup | Popup | popup_id | `@OneToOne(fetch = LAZY)`, `@MapsId`, `NOT NULL` |
| viewCountBoost | long | view_count_boost | `NOT NULL` |
| favoriteCountBoost | long | favorite_count_boost | `NOT NULL` |
| lastBoostedDate | LocalDate | last_boosted_date | |

- 메서드: `wasBoostedOn(date)` - 지정 날짜에 이미 가산했는지 확인
- 메서드: `addBoost(viewCountDelta, favoriteCountDelta, boostedDate)` - 가산값 누적 및 마지막 가산일 갱신

### PopupAdvertisement -> `popup_advertisement`

팝업 광고 설정. `Popup`은 JPA 연관이 아니라 `popupId` 값으로 참조한다. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| popupId | Long | popup_id | `NOT NULL`, `Popup.id` 값 참조 |
| placement | PopupAdvertisementPlacement | placement | `NOT NULL`, ENUM(STRING), len 50 |
| active | boolean | active | `NOT NULL` |
| adStartAt | LocalDateTime | ad_start_at | `NOT NULL` |
| adEndAt | LocalDateTime | ad_end_at | `NOT NULL` |
| priority | int | priority | `NOT NULL` |
| advertiserName | String | advertiser_name | len 100 |
| memo | String | memo | len 500 |
| deletedAt | LocalDateTime | deleted_at | |

### PopupTotalViewCount -> `popup_total_view_count`

팝업 전체 조회수. `BaseEntity`를 상속하지 않는다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| popupUuid | String | popup_uuid | PK, len 36, `Popup.uuid` 값 참조 |
| viewCount | long | view_count | `NOT NULL` |

### PopupSubmission -> `popup_submission`

사용자 팝업 제보. 제출자는 JPA 연관이 아니라 `submitterUserUuid` 값으로 참조한다. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| name | String | name | `NOT NULL`, len 100 |
| startDate | LocalDate | start_date | `NOT NULL` |
| endDate | LocalDate | end_date | `NOT NULL` |
| openTime | LocalTime | open_time | |
| closeTime | LocalTime | close_time | |
| address | String | address | len 255 |
| roadAddress | String | road_address | `NOT NULL`, len 255 |
| region | String | region | `NOT NULL`, len 100 |
| instaPostUrl | String | insta_post_url | len 255 |
| description | String | description | TEXT |
| submitterUserUuid | String | submitter_user_uuid | `NOT NULL`, len 36, `Users.uuid` 값 참조 |
| status | PopupSubmissionStatus | status | `NOT NULL`, ENUM(STRING) |

- 메서드: `updateStatus(popupSubmissionStatus)` - 제보 상태 갱신

### PopupSubmissionImage -> `popup_submission_image`

팝업 제보 이미지. 여러 이미지가 하나의 `PopupSubmission`에 연결된다. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| popupSubmission | PopupSubmission | popup_submission_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| imageUrl | String | image_url | `NOT NULL`, len 1000 |
| sortOrder | int | sort_order | `NOT NULL` |

### PopupSubmissionRecommend -> `popup_submission_recommend`

`PopupSubmission`과 `Recommend` 추천 카테고리의 조인 엔티티. `BaseEntity`를 상속한다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| popupSubmission | PopupSubmission | popup_submission_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| recommend | Recommend | recommend_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |

---

## favorite 도메인

### UserFavorite -> `user_favorite`

사용자와 팝업의 찜 조인 엔티티. `BaseEntity`를 상속하지 않는다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| user | Users | users_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| popup | Popup | popup_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |

---

## keyword 도메인

### UserAlertKeyword -> `user_alert_keyword`

사용자 관심 키워드. `BaseEntity`를 상속하지 않는다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| user | Users | users_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| alertKeyword | String | alert_keyword | `NOT NULL`, len 100 |

- 메서드: `from(users, alertKeyword)` - 사용자와 키워드로 엔티티 생성

---

## alert 도메인

### UserAlert -> `user_alert`

인앱 알림함 기록. `BaseEntity`를 상속하지 않는다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| user | Users | users_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| popup | Popup | popup_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| alertedAt | LocalDateTime | alerted_at | `NOT NULL` |
| readAt | LocalDateTime | read_at | |

- 메서드: `markAsRead()` - `readAt`을 현재 시각으로 갱신

---

## recommend 도메인

### Recommend -> `recommend`

추천 카테고리 마스터. `BaseEntity`를 상속하지 않는다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| uuid | String | uuid | `NOT NULL`, len 36 |
| recommendName | String | recommend_name | `NOT NULL`, `UNIQUE`, len 100 |

- 메서드: `ensureUuid()` - 저장 전 `uuid`가 없으면 UUID 자동 생성

### UserRecommend -> `user_recommend`

사용자와 추천 카테고리의 조인 엔티티. `BaseEntity`를 상속하지 않는다.

| 필드 | 타입 | 컬럼 | 제약 |
|---|---|---|---|
| id | Long | id | PK, IDENTITY |
| user | Users | users_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |
| recommend | Recommend | recommend_id | `@ManyToOne(fetch = LAZY)`, `NOT NULL` |

---

## Enum 타입 정리

### 엔티티에 저장되는 enum

| Enum | 도메인 | 값 | 비고 |
|---|---|---|---|
| Provider | users | GOOGLE, APPLE, KAKAO | `Users.provider`, ENUM(STRING) |
| Role | users | ADMIN, MEMBER | `Users.role`, ENUM(STRING), `toAuthority()` 보유 |
| MediaType | popup | IMAGE, CAROUSEL_ALBUM, VIDEO | `Popup.mediaType`, ENUM(STRING) |
| PopupAdvertisementPlacement | popup | USER_RECOMMEND_TOP | `PopupAdvertisement.placement`, ENUM(STRING) |
| PopupSubmissionStatus | popup | PENDING, APPROVED, REJECTED | `PopupSubmission.status`, ENUM(STRING) |

### 표현/조회용 enum

| Enum | 도메인 | 값 | 비고 |
|---|---|---|---|
| SortStandard | popup | LIKES, DISTANCE | 앱 팝업 조회 정렬 기준 |
| MapSortStandard | popup | CLOSEST, MOST_FAVORITED, MOST_VIEWED, NEWEST, CLOSING_SOON | 지도 팝업 조회 정렬 기준 |
| HomeSortStandard | popup | MOST_FAVORITED, MOST_VIEWED, NEWEST, CLOSING_SOON | 홈 팝업 조회 정렬 기준 |
| PopupSubmissionStatusFilter | popup | ALL, PENDING, APPROVED, REJECTED | 관리자 제보 목록 상태 필터. `ALL`은 상태 조건 없음 |

참고: `common.enums.Role`도 `ADMIN`, `MEMBER` 값을 가지지만, 현재 엔티티 필드에서 사용하는 권한 enum은 `domain.users.entity.Role`이다.

---

## 제약 및 관계 전략

> 아래 내용은 현재 엔티티 코드의 JPA 어노테이션 기준이다. 실제 운영 DB DDL이나 수동 마이그레이션 결과와 다를 수 있다.

- `@Table(uniqueConstraints = ...)`, `@Table(indexes = ...)`, `@Index`, `@UniqueConstraint` 선언은 현재 엔티티 코드에 없다.
- 코드상 컬럼 단위 `unique = true`는 `users.uid`, `users.nickname`, `recommend.recommend_name`, `popup.insta_post_id`에만 있다.
- `@ManyToOne` 관계는 모두 단방향 child-to-parent이며 `fetch = FetchType.LAZY`, `@JoinColumn(nullable = false)`를 사용한다.
- `PopupCountBoost`만 `@OneToOne(fetch = LAZY)` + `@MapsId` 공유 PK 전략을 사용한다.
- `@OneToMany`, `@ManyToMany`, cascade, orphan removal 선언은 현재 엔티티 코드에 없다.
- 일부 관계는 JPA 연관관계 없이 값 컬럼으로 표현한다: `PopupAdvertisement.popupId`, `PopupTotalViewCount.popupUuid`, `PopupSubmission.submitterUserUuid`.
- 외부 노출 식별자는 주로 `uuid` 문자열이지만, 조인 엔티티의 JPA 관계는 내부 `Long id` 기반으로 연결된다.
- `BaseEntity` 상속 엔티티만 `created_at`, `updated_at` 감사 필드를 가진다.

---

## 엔티티 관계 개요

> 대부분 물리 FK 선언이 아니라 JPA 어노테이션 또는 값 컬럼 기준의 논리적 관계다.

```text
Users (users)
 ├─ UserFavorite        (users_id)              1:N
 ├─ UserAlertKeyword    (users_id)              1:N
 ├─ UserAlert           (users_id)              1:N
 ├─ UserRecommend       (users_id)              1:N
 └─ PopupSubmission     (submitter_user_uuid)   1:N, 값 참조

Popup (popup)
 ├─ PopupImage          (popup_id)              1:N
 ├─ PopupRecommend      (popup_id)              1:N
 ├─ UserFavorite        (popup_id)              1:N
 ├─ UserAlert           (popup_id)              1:N
 ├─ PopupCountBoost     (popup_id, @MapsId)     1:1
 ├─ PopupAdvertisement  (popup_id)              1:N, 값 참조
 └─ PopupTotalViewCount (popup_uuid)            1:1, 값 참조

Recommend (recommend)
 ├─ PopupRecommend           (recommend_id)     1:N
 ├─ PopupSubmissionRecommend (recommend_id)     1:N
 └─ UserRecommend            (recommend_id)     1:N

PopupSubmission (popup_submission)
 ├─ PopupSubmissionImage     (popup_submission_id)  1:N
 └─ PopupSubmissionRecommend (popup_submission_id)  1:N
```
