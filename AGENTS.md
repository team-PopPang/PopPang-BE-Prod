# AGENTS.md

이 파일은 이 저장소에서 작업하는 Codex 에이전트를 위한 운영 지침서다. README, CLAUDE.md, onboarding 문서는 오래된 설명이 남아 있을 수 있으므로, 에이전트 작업 기준은 이 파일과 배포 런북 [`DEPLOYMENT.md`](./DEPLOYMENT.md)를 우선한다.

## 프로젝트 개요

**PopPang**은 사용자가 등록한 키워드와 관련된 팝업 스토어 정보를 제공하고 알림 대상 데이터를 관리하는 모바일 앱 백엔드다. 이 저장소는 iOS/Android 클라이언트와 일부 웹/관리 화면을 위한 **Spring Boot REST API 서버**다.

## 현재 기술 스택

- **Java 17**(Gradle toolchain) / **Spring Boot 3.5.6** / **Gradle Wrapper 8.14.3**
- **Spring Data JPA + MySQL**, **Redis**(Lettuce 단일 노드), **Spring Scheduling**
- **Spring Security** + 자체 JWT(`io.jsonwebtoken:jjwt 0.12.6`, HS256, `typ` claim)
- 소셜 로그인: **Kakao**, **Google**(`google-api-client 2.2.0`), **Apple**(`nimbus-jose-jwt 10.3`, ES256 `.p8`)
- **SpringDoc OpenAPI 2.7.0**, **Actuator**, `jakarta.mail` 직접 SMTP 발송
- 포맷: **Spotless + google-java-format 1.17.0** (2-space indentation)

## 명령어

```bash
# 빌드
./gradlew clean bootJar
./gradlew build

# 테스트
./gradlew test

# 포맷
./gradlew spotlessApply
./gradlew spotlessCheck

# 로컬 실행: 기본 profile은 prod이므로 보통 local 명시
./gradlew bootRun --args='--spring.profiles.active=local'

# 수동 배포: 상세는 DEPLOYMENT.md
make getKey
make prod-deploy VERSION=x.y.z
```

- `src/main/resources/application*.yml`과 현재 Apple key 파일 `src/main/resources/auth/AuthKey_382T2TB4RW.p8`는 `.gitignore` 대상이다. 클린 클론에는 실행 설정이 없을 수 있다.
- Apple key ignore 규칙은 `AuthKey_382T2TB4RW.p8` 파일명 하나만 대상으로 하며 `*.p8` 와일드카드가 아니다. 다른 파일명의 교체 키는 생성·다운로드·스테이징·사용 전에 해당 경로를 `.gitignore`에 추가하고 `git check-ignore -v <path>`로 적용을 확인한다.
- 테스트/로컬 실행에는 DB, Redis, `jwt.secret`, `jwt.access-token-exp-minutes`, `jwt.refresh-token-exp-days`, `jwt.issuer` 등 private config가 필요하다. 예전 문서처럼 `JWT_SECRET` 하나만 주입한다고 충분하다고 가정하지 않는다.
- GitHub Actions에는 PR용 `build-test.yml`(`./gradlew clean build`)과 `main` push용 `cicd.yml`(bootJar, Docker build, 원격 deploy)이 존재한다. 로컬 `makefile` 수동 배포도 병행된다.
- `cicd.yml`은 현재 `APP_NAME: poppang-dev`로 이미지를 만들면서 `deploy-prod.sh`를 호출한다. 운영 배포로 신뢰하기 전에 의도와 대상 서버를 반드시 확인한다.

## 아키텍처

`com.poppang.be` 아래는 크게 **`common`**(횡단 인프라)과 **`domain`**(도메인별 로직)으로 나뉜다. 도메인은 대체로 아래 레이어를 따르지만, 필요한 패키지만 둔다.

```text
domain/<domain>/
├── presentation/       # @RestController
├── application/        # Service, batch, scheduler, storage helper
├── infrastructure/     # JpaRepository, projection
├── dto/                # request/response, popup은 app/web 분리
├── entity/             # @Entity 및 엔티티 enum
├── enums/              # 표현/필터/정렬용 enum
└── mapper/             # 다건 변환, N+1 방지용 배치 조립
```

중요한 구조 예외:

- `auth`는 `apple`, `google`, `kakao` provider별 하위 패키지와 `auth.redis` refresh token 저장소를 가진다.
- `popup`은 가장 큰 도메인이다. `presentation/app`, `presentation/web`, `dto/app`, `dto/web`, `mapper`, `infrastructure/projection`이 있다.
- `mapper`와 `projection`은 현재 주로 `popup` 도메인에 있다.
- `TokenService`, `PopupCountBoostService`, 배치/스케줄러/스토리지 계열은 인터페이스 없이 단독 서비스/컴포넌트로 존재한다.

## 도메인 요약

| 도메인 | 역할 | 주의 |
|---|---|---|
| `auth` | 소셜 로그인 3종, 자동 로그인, hidden 토큰 발급/갱신 | 자체 JWT는 운영 로그인 응답에 아직 본격 연결되지 않음 |
| `users` | 회원, 닉네임, 탈퇴/복구, FCM 토큰, 알림 수신 동의 | 식별은 소셜 `uid`와 앱 내부 `uuid`가 공존 |
| `popup` | 팝업 조회/검색/추천/필터/광고/제보/관리/조회수 | app/web 분리, 보조 엔티티와 native query가 많음 |
| `favorite` | 찜하기(`UserFavorite`) | 좋아요수 노출 시 `PopupCountBoost` 포함 |
| `keyword` | 사용자 관심 키워드 저장 | 푸시 발송 로직 아님, 정규화/중복 제약 없음 |
| `alert` | 인앱 알림함(`UserAlert`) 기록 CRUD | 푸시 발송 로직 아님 |
| `recommend` | 추천 카테고리 마스터 데이터 조회 | 개인화 추천 구현은 `popup` 쪽에 있음 |

## 컨트롤러 컨벤션

- 기본 형태는 `@RestController` + `@RequestMapping` + `@RequiredArgsConstructor` 생성자 주입이다.
- 목표 URL 규칙은 앱 `/api/v1/<resource>`, 웹 `/api/v1/web/<resource>`, 회원 종속 `/api/v1/users/{userUuid}/...`다.
- 기존 경로 예외가 많다. `/api/v1/user`(단수), `/api/v1/favorite`, `/api/v1/alert-keyword`, `/api/v1/recommend/web`, `/api/v1/admin`, `/api/v1/popup-submissions`는 호환성 때문에 무단 변경하지 않는다.
- Swagger `@Operation`/`@Tag`를 붙인다. 내부/테스트성 endpoint는 `@Hidden`을 붙이되, `@Hidden`은 보안이 아니다.
- 응답 래핑은 혼재한다. 신규 코드는 컨트롤러 단위로 `ApiResponse<T>` 또는 `ResponseEntity<T>` 중 하나로 통일한다. 기존에는 `RecommendController`, `PopupUserController`처럼 한 클래스 안에서도 혼재한 예외가 있다.
- 본문 없는 성공 응답은 기존 코드와 맞춰 `ResponseEntity.ok().build()`를 우선 사용한다.

## 서비스 컨벤션

- 도메인 유스케이스 서비스는 보통 `XxxService` 인터페이스 + `XxxServiceImpl` 구현을 둔다.
- 토큰, 카운트 보정, 배치, 스케줄러, 이미지 저장 같은 보조 서비스는 단독 `@Service`/`@Component`가 이미 있으므로 기존 패턴을 따른다.
- 트랜잭션은 메서드 단위로 붙인다. 쓰기 `@Transactional`, 조회 `@Transactional(readOnly = true)`.
- 복잡한 다건 조립은 DTO의 `from()`에 밀어 넣지 말고 mapper/service에서 ID 리스트 배치 조회 후 Map으로 조립한다.

## 엔티티와 식별자

- 기본 패턴은 `@Entity`, `@Getter`, `@Table(name = "snake_case")`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, `@Builder`다.
- setter는 피하고 `changeNickname()`, `softDelete()`, `deactivate()` 같은 의미 있는 도메인 메서드로 상태를 바꾼다.
- 연관관계는 가능하면 `LAZY`를 쓴다. `@ManyToOne(fetch = FetchType.LAZY)`가 기본이다.
- 생성/수정 시각이 필요한 엔티티는 `BaseEntity`를 상속한다. JPA Auditing이 값을 채우므로 수동 세팅하지 않는다.
- 핵심 외부 노출 리소스(`Users`, `Popup`, `Recommend`)는 보통 `Long id` + `String uuid` + `@PrePersist` UUID 생성 패턴이다.
- 예외가 있다. `PopupSubmission`은 admin API에서 Long id가 path로 노출되고, `PopupTotalViewCount`는 `popup_uuid` 문자열 PK이며, `PopupCountBoost`는 `popup_id` 공유 PK(`@MapsId`)다.
- popup 보조 엔티티의 참조 방식은 혼재한다. `PopupImage`, `PopupRecommend`, `PopupCountBoost` 등은 JPA 연관을 쓰고, `PopupAdvertisement`, `PopupTotalViewCount`는 id/uuid 값으로 느슨하게 참조한다.
- enum 필드는 `@Enumerated(EnumType.STRING)`을 사용한다.

## DTO와 매핑

- 신규 DTO 이름은 `...RequestDto`, `...ResponseDto`를 쓴다. 기존 오타/누락 suffix는 호환성 때문에 무단 리네임하지 않는다.
- 대표적인 고정 오타/예외: `UserUpdateFcmTokenResquestDto`, `PopPopupSubmissionResponseDto`, `UserAlertKeywordDeleteDto`, `RegionDistrictsResponse`.
- 단순 DTO는 `record`를 허용하지만, 기존 DTO 대부분은 Lombok `@Getter`, `@NoArgsConstructor`, `@Builder`, 정적 팩토리 `from()` 패턴이다.
- boolean JSON 키를 `isXxx`로 노출해야 하면 `@JsonProperty("isXxx")`를 명시한다.
- `PopupResponseDto.from()`과 `PopupUserResponseDto.from()`은 이미지/추천/카운트/찜 여부를 모두 채우는 완전 매핑이 아니다. 목록/상세 응답은 mapper/service의 배치 조립 경로를 확인한다.

## 예외와 검증

- 신규 비즈니스 오류는 `throw new BaseException(ErrorCode.XXX)`로 처리한다.
- 조회 실패는 `orElseThrow(() -> new BaseException(ErrorCode.XXX_NOT_FOUND))` 패턴을 쓴다.
- `RuntimeException`, `IllegalArgumentException`, `IllegalStateException`을 그대로 던지면 `GlobalExceptionHandler`가 보통 `6000 INTERNAL_ERROR`로 뭉뚱그린다. auth provider/JWT legacy 코드에는 raw exception이 남아 있으므로, 수정할 때 점진적으로 `ErrorCode`로 전환한다.
- `ErrorCode` 대역: 4000 알림/키워드, 4100 찜, 4200 유저/권한, 4300 팝업/제보, 5000 인증/JWT, 6000 시스템.
- `@Valid`/`jakarta.validation`은 현재 거의 쓰지 않는다. 새 기능도 서비스 레이어 수동 검증(null/blank, 중복, enum 파싱)을 우선 적용한다.
- 주석, Swagger 설명, `ErrorCode` 메시지는 한국어를 유지한다. 식별자는 영어 camelCase, DB 컬럼은 snake_case다.

## 인증과 보안

- 자체 JWT는 `common/jwt/JwtProvider`를 통해 생성/검증한다. 직접 `Jwts` 파싱 코드를 새로 만들지 않는다.
- JWT는 HS256, `sub=userUuid`, `issuer`, `typ=ACCESS|REFRESH`를 사용한다. refresh token은 Redis `auth:refresh:{userUuid}`에 TTL과 함께 저장한다.
- `JwtAuthenticationFilter`는 Bearer access token이 있으면 검증 후 `Users.uuid`를 principal로 넣고 `domain.users.entity.Role.toAuthority()`로 권한을 만든다.
- `SecurityConfig`는 STATELESS, csrf/httpBasic/formLogin 비활성이다.
- URL 레벨은 사실상 `anyRequest().permitAll()`이다. Bearer token이 있으면 인증 컨텍스트를 채우지만, token이 없어도 URL 차단은 거의 없다.
- 기본 Swagger 경로(`/swagger-ui`, `/v3/api-docs` 등)는 차단하지만, `springdoc.*.path`로 설정된 커스텀 API docs/UI 경로와 UI asset 경로는 허용된다.
- 관리자 API 보호 방식은 혼재한다. `deactivatePopupV2`는 `@PreAuthorize("hasRole('ADMIN')")`, 다른 제보 관리 API는 `uuid` query param을 서비스에서 수동 검증한다. `updateSubmissionStatus`처럼 관리자 검사가 빠진 legacy endpoint도 있다.
- 신규 보호 endpoint는 반드시 `@PreAuthorize` 또는 명시적 관리자 검증을 추가한다. `@Hidden`이나 Swagger 제외로 보호했다고 간주하지 않는다.
- `Role` enum은 `domain.users.entity.Role`과 `common.enums.Role` 두 개가 있다. 실제 권한에는 `domain.users.entity.Role`을 사용한다.

## 배포와 설정

- 배포 상세 절차는 [`DEPLOYMENT.md`](./DEPLOYMENT.md)를 따른다.
- 로컬 `makefile`은 `APP_NAME=poppang-prod`, 기본 `VERSION=1.2.3`이다. 배포 시 항상 `VERSION=x.y.z`를 명시한다.
- `make getKey`는 private repo(`team-PopPang/PopPang-Private`, branch `BE`)에서 Apple `AuthKey_382T2TB4RW.p8`와 `application-prod.yml`을 받지만, `application.yml`은 받지 않는다.
- GitHub Actions는 private config를 별도로 다운로드해 빌드/배포한다. workflow secret 이름은 로컬 `.env`의 `GITHUB_ACCESS_TOKEN`과 다르다.
- `make getKey`의 `curl -s`는 404 응답도 파일로 저장할 수 있다. 갱신 전후 `curl -f`와 파일 sanity check를 수행한다.
- Dockerfile은 `build/libs/*.jar`만 이미지에 복사하지만, JAR 안에는 빌드 시점의 private config와 Apple key가 포함될 수 있다. `.dockerignore`가 현재 없으므로 Docker build context 노출도 주의한다.
- 팝업 제보 이미지는 기본적으로 `/opt/submission_images`에 저장되고 URL prefix는 `/submissionImages`다. 원격 배포 스크립트에서 persistent volume과 정적 서빙/프록시 매핑을 보장하는지 확인해야 한다.

## 주요 함정

- 푸시 발송은 이 백엔드에 없다. `firebase-admin`, `FirebaseMessaging`, APNs 호출이 없다. FCM 토큰은 `Users.fcm_token`에 저장만 한다.
- 키워드 매칭/푸시는 외부 cron/worker가 `GET /api/v1/user/with-alert-keyword/a` 또는 `/b`를 폴링해 수행하는 구조다. 대상 조건은 `is_deleted=0 AND is_alerted=1`이다.
- `src/main/resources/auth/AuthKey_382T2TB4RW.p8`는 푸시 인증 키가 아니라 Apple 로그인 client secret 서명용이다.
- 개인화 추천은 `recommend` 도메인이 아니라 `PopupServiceImpl`/`PopupUserServiceImpl` 쪽에 있다. `recommend` 도메인은 카테고리 마스터 조회 성격이다.
- 조회수/좋아요수 노출에는 `PopupCountBoost` 가산값을 포함해야 한다. `PopupCountBoostScheduler`는 매일 KST 03:00에 랜덤 가산을 수행한다.
- 조회수 increment는 Redis `popup:view:{popupUuid}:delta`에 70초 TTL로 누적되고, `PopupTotalViewCountFlushScheduler`가 60초마다 DB에 flush한다. DB만 직접 보면 최근 증가분이 빠질 수 있다.
- 비회원 `PopupController.getAllPopupList`는 비활성 팝업까지 반환한다. 활성 필터가 필요한 화면은 해당 service/repository 경로를 확인한다.
- `keyword`는 정규화 없이 입력 그대로 저장한다. `StringNormalizer`는 popup 지역/구 정규화용이다.
- `PopupRepository` native query는 MySQL 의존적이다. `SUBSTRING_INDEX`, Haversine 거리 계산, `RAND()`와 주소 문자열 포맷에 민감하다.
- 팝업 제보 승인 flow는 원본 `popup_submission`을 직접 운영 팝업으로 쓰는 것이 아니라, admin update request의 최종 값을 `popup`, `popup_image`, `popup_recommend`에 저장한다.
- `UsersController`의 `/{userUuid}/resotre`, `UserUpdateFcmTokenResquestDto`, `PopPopupSubmissionResponseDto`처럼 굳어진 오타는 기존 호환을 위해 무단 변경하지 않는다. 신규 코드는 정확한 철자를 쓴다.
- `UserUpdateFcmTokenResquestDto`는 `popup.dto.app.response` 패키지에 있지만 `users` 도메인이 import한다. 신규 DTO는 자기 도메인에 둔다.
- 소셜 로그인 응답은 현재 user profile 중심이고, 자체 JWT 발급은 hidden `/api/v1/auth/token/test`와 `/refresh`에 분리되어 있다. `/autoLogin`은 uuid만으로 유저를 조회한다.
- 루트 `build/`, `poppang-prod-*.tar`, JAR, zip 등 산출물은 `.gitignore` 처리된 로컬 산출물이다.
