# README Developer Onboarding Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stale root README with an accurate developer-onboarding entry point for the current PopPang backend.

**Architecture:** Keep `README.md` focused on project orientation, safe local startup, high-level architecture, and document routing. Treat `AGENTS.md` as the detailed development source of truth and `DEPLOYMENT.md` as the deployment and operations source of truth, avoiding duplicated runbook content.

**Tech Stack:** Markdown, Java 17, Spring Boot 3.5.6, Gradle Wrapper 8.14.3, shell-based static verification with `rg` and `git diff --check`.

## Global Constraints

- Write the README for a new PopPang backend developer.
- Keep Korean as the primary language for prose, comments, warnings, and descriptions.
- Do not include secrets, private configuration values, or copyable credential examples.
- Do not hard-code a Swagger URL; direct readers to `springdoc.swagger-ui.path` and `springdoc.api-docs.path`.
- Always show the `local` Spring profile in the local run command because the default profile is `prod`.
- State that this repository stores FCM tokens and keyword targets but does not send Firebase/APNs push notifications.
- Do not claim that every API is protected by JWT or that JWT is fully integrated with every social-login response.
- Keep detailed coding conventions in `AGENTS.md` and deployment procedures in `DEPLOYMENT.md`.
- Do not modify application code, configuration, CI/CD, or deployment scripts.

---

### Task 1: Replace README with the approved developer-onboarding content

**Files:**

- Modify: `README.md:1`
- Reference: `docs/superpowers/specs/2026-07-12-readme-rewrite-design.md`
- Reference: `AGENTS.md`
- Reference: `DEPLOYMENT.md`
- Reference: `build.gradle`
- Reference: `gradle/wrapper/gradle-wrapper.properties`
- Reference: `.github/workflows/build-test.yml`
- Reference: `.github/workflows/cicd.yml`

**Interfaces:**

- Consumes: The approved README design and the current repository facts listed above.
- Produces: A root `README.md` that routes developers to the correct setup, architecture, API, and operations information.

- [ ] **Step 1: Replace the complete README**

Set `README.md` to the following exact content:

````markdown
# PopPang Backend

> PopPang 모바일 앱과 일부 웹·관리 화면을 위한 Spring Boot REST API 서버입니다.

이 README는 저장소를 처음 접하는 개발자를 위한 시작점입니다. 세부 구현 규칙은 [AGENTS.md](./AGENTS.md), 배포·검증·롤백 절차는 [DEPLOYMENT.md](./DEPLOYMENT.md)를 기준으로 합니다. 문서 내용이 충돌하면 두 기준 문서를 우선합니다.

## 프로젝트 개요

PopPang은 사용자가 관심 있는 팝업 스토어를 조회하고 찜, 관심 키워드, 인앱 알림 데이터를 관리할 수 있도록 지원합니다. 이 저장소는 iOS·Android 클라이언트와 일부 웹·관리 화면에서 사용하는 API를 제공합니다.

## 주요 기능

| 영역 | 기능 |
|---|---|
| 팝업 | 목록·상세·검색, 진행/예정 조회, 지역·거리 필터, 랜덤·연관 팝업, 앱·웹 응답 |
| 인증·회원 | Kakao·Google·Apple 소셜 로그인, 회원 프로필·닉네임·탈퇴·복구, 알림 동의와 FCM 토큰 관리 |
| 찜·키워드·알림함 | 찜 등록·삭제·목록·카운트, 관심 키워드 관리, 인앱 알림 기록 조회·읽음·삭제 |
| 추천 | 추천 카테고리 조회, 회원별 팝업 추천과 랜덤 보충 |
| 제보·관리 | 팝업 제보 이미지 업로드, 관리자 조회·승인·반려·비활성화 |
| 조회수 | Redis에 증가분을 누적한 뒤 주기적으로 DB에 반영하고 노출용 가산값을 적용 |

> [!IMPORTANT]
> 이 백엔드는 FCM 토큰과 키워드 알림 대상 데이터를 저장하지만 Firebase/APNs 푸시를 직접 발송하지 않습니다. 실제 키워드 매칭과 푸시 발송은 외부 cron 또는 worker가 대상 조회 API를 폴링하는 구조입니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.6 |
| Build | Gradle Wrapper 8.14.3 |
| Persistence | Spring Data JPA, MySQL |
| Cache·State | Redis, Lettuce |
| Security | Spring Security, 자체 JWT(JJWT 0.12.6) |
| Social Login | Kakao, Google API Client 2.2.0, Nimbus JOSE JWT 10.3(Apple) |
| API·Operations | SpringDoc OpenAPI 2.7.0, Actuator, Spring Scheduling |
| Mail | Jakarta Mail 기반 SMTP |
| Formatting | Spotless, google-java-format 1.17.0 |

## 아키텍처

`com.poppang.be` 아래는 공통 인프라와 도메인 로직으로 구분됩니다.

```text
src/main/java/com/poppang/be
├── common
│   ├── config          # Spring, Redis 등 공통 설정
│   ├── entity          # BaseEntity
│   ├── exception       # ErrorCode, BaseException, 전역 예외 처리
│   ├── jwt             # JWT 생성·검증
│   ├── mail            # SMTP 메일 발송
│   ├── response        # 공통 API 응답
│   ├── security        # Security 설정과 인증 필터
│   └── util            # 공통 유틸리티
└── domain
    ├── alert           # 인앱 알림함
    ├── auth            # 소셜 로그인, JWT, Redis refresh token
    ├── favorite        # 팝업 찜
    ├── keyword         # 사용자 관심 키워드
    ├── popup           # 팝업 조회·추천·제보·관리·조회수
    ├── recommend       # 추천 카테고리 마스터
    └── users           # 회원 정보와 상태
```

도메인은 필요에 따라 다음 레이어를 사용합니다.

```text
presentation/       REST controller
application/        service, batch, scheduler, storage
infrastructure/     JPA repository, projection
dto/                request/response DTO
entity/             JPA entity
enums/              표현·필터·정렬 enum
mapper/             배치 조회 기반 응답 조립
```

`popup`은 앱·웹 controller/DTO, mapper, projection으로 세분화된 가장 큰 도메인입니다. `auth`는 Kakao·Google·Apple provider별 구현과 Redis refresh token 저장소를 포함합니다. 개인화 팝업 추천은 `recommend`가 아니라 `popup` 서비스에 있습니다.

## 로컬 개발 시작하기

### 1. 사전 조건

- JDK 17
- MySQL
- Redis
- 저장소의 private config에 접근할 권한
- 소셜 로그인 검증 시 각 OAuth provider 설정과 Apple `.p8` 키
- 신규 가입 메일 검증 시 SMTP 설정

### 2. 저장소 받기

```bash
git clone https://github.com/team-PopPang/PopPang-BE-Prod.git
cd PopPang-BE-Prod
```

### 3. Private config 준비

다음 파일은 `.gitignore` 대상이므로 클린 클론에 없을 수 있습니다.

- `src/main/resources/application*.yml`
- `src/main/resources/auth/*.p8`
- `.env`

기본 실행에는 MySQL, Redis와 아래 JWT 설정이 필요합니다.

- `jwt.secret`
- `jwt.access-token-exp-minutes`
- `jwt.refresh-token-exp-days`
- `jwt.issuer`

소셜 로그인이나 메일 기능을 검증할 때는 OAuth, Apple key, SMTP 설정도 준비합니다. 비밀값을 README나 커밋, 채팅, 로그에 남기지 마세요. Private config 확보와 갱신 절차는 팀 관리자와 [DEPLOYMENT.md](./DEPLOYMENT.md)를 확인하세요.

> [!CAUTION]
> 기본 Spring profile은 `prod`입니다. 로컬 실행에서는 반드시 `local` profile을 명시하고, 테스트·빌드 전에 DB와 Redis가 안전한 로컬 또는 테스트 자원을 가리키는지 확인하세요.

### 4. 로컬 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 5. 개발 명령

```bash
# 실행 가능한 JAR 생성
./gradlew clean bootJar

# 컴파일, 테스트, 검증 전체 실행
./gradlew build

# 테스트
./gradlew test

# 포맷 적용 및 검사
./gradlew spotlessApply
./gradlew spotlessCheck
```

`build`와 `test`도 application context를 구성하는 과정에서 private DB, Redis, JWT 설정이 필요할 수 있습니다.

## API 안내

대표 API prefix는 다음과 같습니다. 기존 클라이언트 호환을 위해 일부 경로의 단수형이나 명명 예외가 유지되고 있습니다.

| 영역 | 대표 경로 |
|---|---|
| 인증 | `/api/v1/auth` |
| 비회원 팝업 | `/api/v1/popup` |
| 회원 팝업 | `/api/v1/users/{userUuid}/popups` |
| 웹 팝업 | `/api/v1/web/popup` |
| 팝업 제보 | `/api/v1/popup-submissions` |
| 관리자 | `/api/v1/admin` |
| 회원 | `/api/v1/user` |
| 찜 | `/api/v1/favorite` |
| 관심 키워드 | `/api/v1/alert-keyword` |
| 인앱 알림함 | `/api/v1/users/{userUuid}/alert` |
| 추천 카테고리 | `/api/v1/recommend` |

전체 endpoint와 request/response 형식은 실행 환경의 OpenAPI 문서를 확인하세요.

- Swagger UI: `application*.yml`의 `springdoc.swagger-ui.path`
- OpenAPI JSON: `application*.yml`의 `springdoc.api-docs.path`

기본 `/swagger-ui`와 `/v3/api-docs` 경로는 차단될 수 있으므로 고정 URL을 가정하지 않습니다. `@Hidden` endpoint는 문서에서만 숨겨질 뿐 보안상 보호되었다는 의미가 아닙니다.

## 현재 구현상 주의사항

- 자체 JWT 생성·검증과 Redis refresh token 저장 기능은 있지만, 일반 소셜 로그인 응답 전체에 일관되게 연결된 상태는 아닙니다.
- Bearer access token이 있으면 인증 컨텍스트를 구성하지만 모든 URL이 일괄적으로 인증 필수인 것은 아닙니다. 보호 endpoint를 추가하거나 변경할 때 명시적인 권한 검증을 확인하세요.
- 최근 팝업 조회수 증가분은 Redis에 잠시 머문 뒤 DB에 반영됩니다. DB 값만 조회하면 아직 flush되지 않은 증가분이 빠질 수 있습니다.
- 팝업 조회수와 좋아요수 노출에는 `PopupCountBoost` 가산값이 포함될 수 있습니다.
- 테스트는 현재 팝업 제보·관리, 조회수 가산, 이미지 저장소와 application context 확인에 집중되어 있습니다.
- MySQL native query와 주소 문자열 형식에 의존하는 팝업 검색·거리 계산 로직이 있습니다.

더 자세한 인증, 응답, DTO, 엔티티, 예외 처리 규칙과 도메인별 함정은 [AGENTS.md](./AGENTS.md)를 확인하세요.

## CI/CD와 배포

- `main` 대상 pull request에서는 GitHub Actions가 private config를 받은 뒤 `./gradlew clean build`를 실행합니다.
- `main` push 또는 수동 실행은 이미지 빌드와 원격 배포 workflow를 실행할 수 있습니다.
- 현재 CD에는 `poppang-dev` 이미지명과 `deploy-prod.sh` 호출이 혼재하므로 대상과 의도를 확인하기 전 운영 배포로 신뢰하지 않습니다.
- 루트에서 인자 없이 `make`를 실행하면 private config 다운로드 후 실제 배포까지 이어질 수 있습니다.

수동 배포, health check, 태그, 롤백, private config 갱신 절차는 반드시 [DEPLOYMENT.md](./DEPLOYMENT.md)를 따르세요.

## 개발 문서

- [AGENTS.md](./AGENTS.md): 코드 구조, 구현 규칙, 보안, 주요 함정
- [DEPLOYMENT.md](./DEPLOYMENT.md): 배포, 검증, 롤백, 운영 위험
````

- [ ] **Step 2: Verify internal file links**

Run:

```bash
test -f AGENTS.md
test -f DEPLOYMENT.md
test -f docs/superpowers/specs/2026-07-12-readme-rewrite-design.md
```

Expected: all commands exit with status 0 and produce no output.

- [ ] **Step 3: Verify versions, commands, and representative API prefixes against sources**

Run:

```bash
rg -n "JavaLanguageVersion.of\(17\)|org.springframework.boot.*3.5.6|googleJavaFormat\('1.17.0'\)" build.gradle
rg -n "gradle-8.14.3-bin.zip" gradle/wrapper/gradle-wrapper.properties
rg -n "@RequestMapping\(\"/api/v1/(auth|popup|user|favorite|alert-keyword|recommend|admin|popup-submissions)" src/main/java/com/poppang/be/domain
```

Expected: each command finds the documented version or at least one representative controller prefix.

- [ ] **Step 4: Verify stale or unsafe guidance is absent**

Run:

```bash
! rg -n "ddl-auto: update|be-0.0.1-SNAPSHOT.jar|localhost:8080/swagger-ui/index.html|추후 개발 예정|고do화" README.md
rg -n 'spring.profiles.active=local|기본 Spring profile은 `prod`|푸시를 직접 발송하지 않습니다|인자 없이 `make`' README.md
```

Expected: the first command exits with status 0 because none of the stale phrases are present; the second command finds all four safety statements.

- [ ] **Step 5: Verify Markdown structure and whitespace**

Run:

```bash
awk '/^```/{count++} END {exit count % 2}' README.md
git diff --check
```

Expected: both commands exit with status 0 and produce no output.

- [ ] **Step 6: Review the documentation-only diff**

Run:

```bash
git diff -- README.md docs/superpowers/plans/2026-07-12-readme-rewrite.md
git status --short
```

Expected: the README replacement and this implementation plan are the only uncommitted changes.

- [ ] **Step 7: Commit the completed README rewrite**

Run:

```bash
git add README.md docs/superpowers/plans/2026-07-12-readme-rewrite.md
git commit -m "docs: 개발자 온보딩 README 개편"
```

Expected: one commit containing the README rewrite and implementation plan, with no application-code changes.
