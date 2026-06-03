# CLAUDE.md

이 파일은 이 저장소에서 작업하는 Claude Code 에이전트를 위한 운영 지침서다. 알아내기 어려운 컨벤션·함정 위주로 정리한다. 배포 상세 절차는 루트 [`.AGENTS.md`](./.AGENTS.md)(배포 런북) 참조.

## 프로젝트 개요

**PopPang**은 사용자가 등록한 키워드와 관련된 팝업 스토어가 새로 생기면 알림을 보내주는 모바일 앱이다. 이 저장소는 iOS/Android 클라이언트와 웹 관리 페이지를 위한 **REST API 서버**다.

## 기술 스택

- **Java 17** (Gradle toolchain) / **Spring Boot 3.5.6** / **Gradle Wrapper 8.14.3**
- **Spring Data JPA + MySQL** (운영), **Redis**(Lettuce, 단일 노드)
- **Spring Security** + **자체 JWT**(`io.jsonwebtoken:jjwt 0.12.6`, HS256)
- 소셜 로그인: **Apple**(`com.nimbusds:nimbus-jose-jwt`, ES256 `.p8`), **Google**(`google-api-client`), **Kakao**
- **SpringDoc OpenAPI**(Swagger), 메일은 `jakarta.mail`(Gmail SMTP 직접 사용)
- 포맷: **Spotless + google-java-format 1.17.0** (2-space 들여쓰기)

## 명령어

```bash
# 빌드
./gradlew clean bootJar            # 운영 배포용 fat JAR (= make build-jar)
./gradlew build                    # 전체 빌드 + 테스트

# 테스트 — JWT_SECRET 환경변수가 반드시 필요하다 (없으면 컨텍스트 로딩 실패)
JWT_SECRET="$(printf '0%.0s' {1..64})" ./gradlew test   # 더미 64자 secret. 운영 secret 사용 금지

# 포맷 (커밋/PR 전 필수)
./gradlew spotlessApply            # google-java-format 자동 적용
./gradlew spotlessCheck            # 포맷 위반 검사

# 로컬 실행 (기본 프로필이 prod이므로 보통 local 지정)
./gradlew bootRun --args='--spring.profiles.active=local'
```

- **CI 빌드/테스트는 없다.** `.github/workflows`는 이슈/PR 자동화(브랜치 생성, 프로젝트 보드, 이메일 알림) 전용이고 빌드·배포는 전적으로 로컬 `makefile` 수동이다.
- 활성 프로필 기본값은 `application.yml`의 `spring.profiles.default=prod`.

## 아키텍처

`com.poppang.be` 아래 **`common`**(횡단 인프라) + **`domain`**(도메인별 로직)으로 나뉜다.

각 도메인은 동일한 레이어 패키지 구조를 따른다:

```
domain/<도메인>/
├── presentation/    # @RestController          (popup만 presentation/app, presentation/web 으로 분리)
├── application/      # XxxService 인터페이스 + XxxServiceImpl
├── infrastructure/   # JpaRepository (+ projection/ 인터페이스 프로젝션)
├── dto/              # request/, response/      (popup만 dto/app, dto/web 으로 분리)
├── entity/           # @Entity (+ 엔티티 속성 enum: Provider, Role, MediaType …)
├── enums/            # 표현용 enum (정렬 기준 등)
└── mapper/           # @Component 매퍼 (다건 변환·배치 조회)
```

**도메인 목록:**

| 도메인 | 역할 | 주의 |
|---|---|---|
| `auth` | 소셜 로그인 3종(kakao/google/apple), 자동 로그인, 자체 JWT 발급/갱신 | provider별 하위 패키지(application/config/dto[/util]) |
| `users` | 회원 핵심 엔티티(`Users`), 닉네임/탈퇴/FCM 토큰/알림 동의 | 식별은 `uid`(소셜) / `uuid`(앱 내부) |
| `popup` | 팝업 조회/검색/추천/필터/광고. 가장 큰 도메인 | app(모바일)·web(관리) 분리, 보조 엔티티 다수 |
| `favorite` | 찜하기(`UserFavorite`, user↔popup 조인) | |
| `keyword` | 사용자 관심 키워드(`UserAlertKeyword`) | 알림 발송이 아니라 데이터 저장만 |
| `alert` | **인앱 알림함**(`UserAlert`) 기록 CRUD | 푸시 발송 로직 아님(아래 함정 참고) |
| `recommend` | 추천 카테고리 마스터 데이터 조회(read-only) | 개인화 추천은 여기 없음(popup에 있음) |

## 핵심 컨벤션 (새 코드는 이렇게 작성)

**컨트롤러**
- `@RestController` + `@RequestMapping("/api/v1/<리소스>")` + `@RequiredArgsConstructor` 생성자 주입(`@Autowired` 필드 주입 금지).
- URL 규칙: 앱 = `/api/v1/<리소스>`, 웹 전용 = `/api/v1/web/<리소스>`, 회원 종속 = `/api/v1/users/{userUuid}/...`.
- Swagger `@Operation`/`@Tag` 부착, 내부/미노출 엔드포인트는 `@Hidden`.
- **응답 래핑은 혼재한다.** 신규 코드는 `ApiResponse<T>` + `ApiResponse.ok(data)`를 표준으로 삼되(웹/토큰/추천 컨트롤러가 모범), 기존 앱 컨트롤러 다수는 `ResponseEntity<T>` raw 반환이다 — 한 컨트롤러 안에서는 한 방식으로 통일한다. 본문 없는 응답은 `ResponseEntity.ok().build()`.

**서비스**
- 인터페이스(`XxxService`) + 구현(`@Service XxxServiceImpl implements XxxService`) 쌍. 컨트롤러는 인터페이스에 의존.
- 트랜잭션은 **메서드 단위**로만 붙인다(클래스 레벨 금지): 쓰기 `@Transactional`, 조회 `@Transactional(readOnly = true)`.

**엔티티**
- `@Entity` + `@Getter` + `@Table(name="snake_case")` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@Builder`. **setter 금지** — 상태 변경은 의미 있는 도메인 메서드(`user.changeNickname()`, `user.softDelete()`, `popup.deactivate()`)로.
- 연관관계는 **항상** `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn`(EAGER 금지).
- 생성/수정 시각이 필요하면 `BaseEntity` 상속(JPA Auditing이 `createdAt`/`updatedAt` 자동 주입 — 수동 세팅 금지). 단순 조인 엔티티는 미상속.
- PK는 `@Id @GeneratedValue(strategy = IDENTITY) Long id`. **외부 노출 식별자는 `String uuid`**(`@PrePersist`의 `ensureUuid()`로 자동 생성). **API 경로/요청 DTO는 항상 uuid를 받고**, 내부 조인/배치는 id를 쓴다 — 둘을 혼동하지 말 것.
- enum 필드는 `@Enumerated(EnumType.STRING)`.

**DTO**
- 요청 `...RequestDto`, 응답 `...ResponseDto`. `@Getter` + `@NoArgsConstructor` + `@Builder` + 정적 팩토리 `from(엔티티)`로 변환. 단순/불변 DTO만 `record` 허용.
- boolean 필드의 JSON 키를 `isXxx`로 노출하려면 `@JsonProperty("isAlerted")`.
- **다건 변환**(목록)은 DTO의 `from()`이 아니라 `mapper` 패키지의 `@Component` 매퍼를 쓴다(ID 리스트로 배치 조회 → Map 조립, N+1 방지). 예: `PopupResponseDto.from()`은 이미지/추천/카운트를 채우지 않는 **부분 매핑**이므로 목록/상세에 쓰지 말 것.

**예외**
- 비즈니스 오류는 **항상** `throw new BaseException(ErrorCode.XXX)`. 조회 실패는 `repository.findByXxx(...).orElseThrow(() -> new BaseException(ErrorCode.XXX_NOT_FOUND))`.
- raw `RuntimeException`/`IllegalArgumentException`을 던지면 `GlobalExceptionHandler`가 `6000 INTERNAL_ERROR(500)`로 뭉뚱그린다 — 의미 있는 에러는 반드시 `ErrorCode`로.
- 새 에러는 `common/exception/ErrorCode.java`의 도메인 코드 대역에 `(HttpStatus, code, "한글 메시지")`로 추가한다. 대역: **4000 알림 / 4100 찜 / 4200 유저 / 4300 팝업 / 5000 인증 / 6000 시스템**.

**기타**
- 입력 검증: `@Valid`/`jakarta.validation`을 **전혀 쓰지 않는다.** 검증은 서비스 레이어에서 수동(null/blank, `existsByNickname` 중복검사 등)으로 한다.
- 복잡 쿼리는 `@Query` 텍스트블록(`""" """`), native는 `nativeQuery=true` + `@Param`. 결과 일부만 필요하면 인터페이스 프로젝션.
- 주석·Swagger 설명·`ErrorCode` 메시지는 **한글**, 식별자는 영어 camelCase(컬럼은 snake_case).

## 인증 / 보안

- **자체 JWT**: `common/jwt/JwtProvider`(HS256, `sub=userUuid`, `typ` 클레임으로 ACCESS/REFRESH 구분). 직접 `Jwts` 파싱하지 말고 `JwtProvider`의 메서드를 쓴다. refresh token은 Redis 키 `auth:refresh:{userUuid}`에 TTL 저장.
- **SecurityConfig**: STATELESS, csrf/httpBasic/formLogin 비활성. 인가는 `/swagger-ui/**`·`/v3/api-docs/**`를 `denyAll()`, **나머지 전부 `anyRequest().permitAll()`**. 즉 **URL 레벨 인가는 사실상 열려 있고**, 보호는 `@EnableMethodSecurity` 기반 `@PreAuthorize("hasRole('ADMIN')")`에만 의존한다 → **보호가 필요한 신규 엔드포인트는 `@PreAuthorize`를 반드시 직접 걸어야 한다(빠뜨리면 무인증 노출).**
- 사용자 principal은 `userUuid`(String). `JwtAuthenticationFilter`가 Bearer 토큰 → `Users` 조회 → `ROLE_` 권한으로 SecurityContext를 채운다.
- 두 개의 `Role` enum이 공존: 실제 권한은 `domain.users.entity.Role`(`toAuthority()` 보유), `common.enums.Role`은 미사용.
- **Redis 용도**: refresh token 저장 + 팝업 조회수 누적/flush.

## 배포 & 시크릿

- **수동 `makefile` 배포** (Docker, `linux/amd64` 강제). 상세/롤백은 [`.AGENTS.md`](./.AGENTS.md) 런북 참조.
  - `make getKey` — private repo(`team-PopPang/PopPang-Private`, branch `BE`)에서 `application-prod.yml`과 Apple `.p8`를 `GITHUB_ACCESS_TOKEN`(루트 `.env`)으로 다운로드.
  - `make prod-deploy VERSION=x.y.z` — bootJar → `docker buildx`(amd64) → tar → `scp poppang-server` → 원격 재기동. **`VERSION`을 항상 명시**(makefile 기본값은 오래됨, 기존 태그 덮어쓰기 금지).
  - 외부 포트 **4002 → 컨테이너 8080**. 헬스체크: `curl -i http://poppang.co.kr:4002/actuator/health` → `{"status":"UP"}`.
- **시크릿 취급 — 엄수**: `src/main/resources/application-prod.yml`, `application-local.yml`, `src/main/resources/auth/*.p8`(Apple 로그인 키), 루트 `.env`에 실제 비밀값(DB/Redis 비밀번호, JWT secret, OAuth client-secret, GitHub PAT)이 들어 있다. 이 파일들은 `.gitignore`로 제외되어 **커밋되지 않고 로컬 전용**이다. **실제 값은 출력·커밋·로그·채팅에 절대 남기지 말 것**(구조/키 이름만). `make getKey`는 HTTP 404 응답도 파일로 저장할 수 있으니 갱신 전 `curl -f`로 검증한다(런북 참고).

## 주요 함정

- **푸시 발송은 이 백엔드에 없다.** `firebase-admin` 의존성도, `FirebaseMessaging`/APNs 호출도 없다. 서버는 FCM 토큰을 `Users.fcm_token`에 **저장만** 하고, 키워드 매칭→푸시는 외부 cron/워커가 `GET /api/v1/users/with-alert-keyword/{a|b}`(`@Tag "[CRON]"`)를 폴링해 수행한다. `auth/*.p8`는 푸시가 아니라 Apple **로그인** 서명용이다.
- **개인화 추천 로직은 `recommend` 도메인이 아니라 `popup`의 `PopupServiceImpl.getRecommendPopupList`에 있다**(UserRecommend 관심 카테고리 → 카테고리당 활성 팝업 2개 → 10개 미달 시 랜덤 보충). `recommend` 도메인은 카테고리 마스터 조회만 한다.
- **조회수/좋아요수는 항상 `PopupCountBoost`(운영자 가산값)를 더해 노출**한다. 새 카운트 노출 코드는 이 가산을 포함해야 한다. 조회수는 Redis delta에 누적 후 스케줄러가 DB로 flush하므로 DB 직접 조회 시 실시간 증가분이 누락될 수 있다.
- **`keyword`는 정규화 없이 입력 그대로 저장**한다(중복 방지 제약 없음). `StringNormalizer`는 keyword가 아니라 popup의 지역/구 정규화 전용이다.
- cron 알림 대상은 `is_deleted=0 AND is_alerted=1`인 유저만. 알림을 끄면(`alerted=false`) 키워드를 등록해도 제외된다.
- 비회원 `PopupController.getAllPopupList`는 **비활성 팝업까지 모두** 반환한다.
- popup의 보조 엔티티(PopupImage/Advertisement/CountBoost/ViewCount)는 JPA FK 연관 없이 `popupId`/`uuid`로 느슨하게 참조한다. 하드 삭제 대신 `deactivate()`(soft) 사용.
- `PopupRepository`의 native `@Query`는 MySQL 의존적이다(`road_address` `SUBSTRING_INDEX` 파싱, Haversine 거리, `RAND()`). 주소 포맷/컬럼명 변경 시 깨지기 쉽다.
- **오타가 굳어진 식별자**가 있다: `UsersController`의 `/{userUuid}/resotre`(restore), `UserUpdateFcmTokenResquestDto`(Request), `PopPopupSubmissionResponseDto`. 기존 호환을 위해 **무단 리네임 금지**, 새 코드는 정확한 철자를 쓴다.
- `UserUpdateFcmTokenResquestDto`는 `popup.dto.app.response` 패키지에 있는데 `users` 도메인이 import해 쓴다(도메인 경계 누수). 신규 DTO는 자기 도메인에 둘 것.
- 인증 구조가 미완이다: 소셜 로그인 응답은 `userUuid` 프로필만 반환하고 운영 플로우에서 자체 JWT를 발급하지 않는다(`TokenService.issueTokens`는 hidden `/token/test`에서만 호출). `/autoLogin`은 uuid만으로 유저를 조회한다.
- 루트의 `build/`, `poppang-prod-*.tar`는 `.gitignore` 처리된 로컬 빌드 산출물이다(소스 아님).
