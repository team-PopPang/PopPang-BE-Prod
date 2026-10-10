# AGENTS.md

이 파일은 이 저장소에서 작업하는 Codex 에이전트를 위한 운영 지침서다. README, CLAUDE.md, onboarding 문서는 오래된 설명이 남아 있을 수 있으므로, 에이전트 작업 기준은 이 파일과 배포 런북 [`DEPLOYMENT.md`](./DEPLOYMENT.md)를 우선한다.

## 작업 승인 규칙

- `git commit`을 실행하기 직전에 커밋 대상 파일과 예정 커밋 메시지를 사용자에게 알리고 명시적 승인을 받는다.
- 사용자의 일반적인 작업 진행 승인이나 이전 커밋 승인을 이후 커밋의 승인으로 간주하지 않는다. 커밋마다 새로 확인한다.
- `git push`는 커밋 승인과 별개다. 사용자가 명시적으로 요청하거나 승인하기 전에는 실행하지 않는다.
- PR 병합은 운영 배포를 시작하므로 커밋·push 승인과 구분하여 명시적 병합 승인을 받는다. 승인된 병합은 Squash merge로 수행한다.

## 브랜치와 에이전트 작업 흐름

팀 공통 규칙은 [README의 개발 워크플로](./README.md#개발-워크플로)를 따른다. 현재 운영 환경은 production 하나이며 `main`이 배포 기준이다. 이슈 생성이나 이슈 번호 연결을 작업의 선행 조건으로 요구하지 않는다.

- 파일 수정 전에 현재 브랜치, 미커밋·미추적 변경, 원격과의 차이를 확인한다. 기존 사용자 작업을 임의로 stash, 삭제, 덮어쓰기하지 않는다.
- 신규 작업은 원격 정보를 갱신한 뒤 최신 `main`에서 `<유형>/<작업명>` 브랜치를 만들어 진행한다. 같은 작업을 이어가는 경우 기존 작업 브랜치를 사용하고, `main`에서 직접 수정하지 않는다.
- 허용 유형은 `feature`, `fix`, `docs`, `refactor`, `test`, `ci`, `chore`다. 에이전트도 `codex/`나 `feat/` 대신 이 유형을 사용한다. 긴급 수정은 `fix/`다.
- 작업명은 영문 소문자로 시작하고 영문 소문자·숫자로 된 단어를 단일 하이픈으로 연결한다. 이슈 번호 접두어를 붙이지 않는다. 정확한 패턴과 예시는 README를 따른다.
- 한 브랜치·PR은 독립적으로 배포 가능한 한 가지 목적에 한정한다. 기능에 필요한 테스트·문서는 같은 브랜치에 포함한다.
- 요청 범위의 구현과 관련 검증을 완료한 뒤 변경 내용, 검증 결과, 남은 위험을 보고한다. 확인하지 않은 검증이나 배포를 완료로 보고하지 않는다.
- PR 대상은 `main`이다. 최신 `main` 반영과 필수 `PR CI` 성공을 확인하고, 승인된 경우에만 Squash merge한다. 관리자도 보호 규칙을 우회하지 않는다. 리뷰어 승인 인원은 필수가 아니다.
- 이미 push한 작업 브랜치에는 `main`을 merge한다. 명시적 승인 없이 공유 브랜치의 rebase나 force push를 실행하지 않는다.
- 병합 후 운영 CI/CD 결과를 확인한다. 브랜치 정리 전에는 PR 병합 여부와 추가 미반영 커밋·파일 변경을 확인한다. Squash merge는 원본 커밋의 조상 관계를 보존하지 않으므로 조상 관계만으로 작업 유실 여부를 판단하지 않는다. 병합된 브랜치는 다음 작업에 재사용하지 않는다.

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

# 로컬 산출물 생성 (운영 배포는 main의 GitHub Actions)
make build-jar
make build-image VERSION=x.y.z
```

- `src/main/resources/application.yml`과 `application-prod.yml`은 실제 비밀값이 없는 커밋된 템플릿이다. 로컬 private 설정, `.env`, `*.p8`은 `.gitignore` 대상이다. 교체 키도 생성·다운로드·스테이징·사용 전에 `git check-ignore -v <path>`로 제외 여부를 확인한다.
- Gradle `test`는 `test` 프로필과 전용 설정을 강제하고 `prod` 프로필 요청을 차단한다. 운영 private config 없이 검증하며, 애플리케이션 로컬 실행에 필요한 DB·Redis·인증 설정과 구분한다.
- `build-test.yml`의 필수 검사 이름은 `PR CI`다. PR 원본 브랜치 이름 검사 후 `./gradlew clean test spotlessCheck --no-daemon`을 실행한다. 이름은 shell 본문에 직접 삽입하지 않고 환경변수로 전달한다. 수동 실행은 이름 검사만 건너뛴다.
- `cicd.yml`은 `main` push 또는 `main` 수동 실행에서 같은 검증을 재실행하고, 성공한 커밋만 `poppang-prod:<short-sha>` 이미지로 빌드·검사·배포한다. 문서만 변경해도 병합하면 재배포가 실행된다.

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

- 현재 운영 배포는 `main`의 GitHub Actions로 수행한다. [`DEPLOYMENT.md`](./DEPLOYMENT.md)에는 이전 미니 PC·수동 배포 설명이 남아 있으므로 현재 워크플로와 [`CI/CD 안전성 스펙`의 AWS 이전 계약](./docs/specs/ci-cd-safety.md)을 함께 확인한다.
- 로컬 `makefile`은 빌드 전용이다. 기본 실행은 도움말이며 `build-jar`, `build-image`, `save-image`를 제공한다. 이미지 생성 시 `VERSION=x.y.z`를 명시한다. `getKey`는 제거됐고 `prod-deploy` 등 운영 배포 경로는 차단된다.
- CI/CD는 private 설정을 다운로드하지 않는다. 운영 설정과 Apple 키는 서버 런타임에서 주입한다.
- `.dockerignore`는 Docker build context를 JAR 산출물로 제한한다. `bootJar`는 `.p8`을 제외하고, JAR·이미지 산출물 검사는 비밀 파일과 실제 비밀값의 포함 여부를 검사한다.
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
