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
│   ├── enums           # 공통 역할 enum
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

## 개발 워크플로

### 브랜치 규칙

현재 운영 환경은 production 하나이며, `main`이 운영 배포 기준 브랜치입니다. 별도 `develop` 브랜치 없이 최신 `main`에서 작업별 단기 브랜치를 만듭니다. 작업을 시작하기 위해 GitHub 이슈를 생성하거나 이슈 번호를 붙이지 않습니다. 사람과 에이전트 모두 같은 규칙을 따릅니다.

브랜치 이름은 `<유형>/<작업명>` 형식입니다.

| 유형 | 용도 | 예시 |
|---|---|---|
| `feature/` | 기능 추가·확장 | `feature/popup-collector-api` |
| `fix/` | 버그·데이터 오류 수정 | `fix/popup-road-address` |
| `docs/` | 문서 변경 | `docs/development-workflow` |
| `refactor/` | 동작을 유지하는 구조 개선 | `refactor/popup-response-mapping` |
| `test/` | 테스트만 추가·개선 | `test/popup-registration` |
| `ci/` | CI/CD 변경 | `ci/branch-name-check` |
| `chore/` | 의존성·도구 등 유지보수 | `chore/update-dependencies` |

- 작업명은 영문 소문자로 시작하고, 영문 소문자·숫자로 된 단어를 단일 하이픈으로 연결합니다. 공백, 밑줄, 대문자, 추가 `/`, 연속·끝 하이픈은 사용하지 않습니다.
- 이슈 번호 접두어는 사용하지 않습니다. `feature/123-popup-api`는 허용하지 않으며 `feature/v2-popup-api`처럼 작업을 설명하는 숫자는 사용할 수 있습니다.
- 기능 브랜치는 `feature/`로 통일합니다. `feat/`, `codex/`는 사용하지 않고, 긴급 수정도 `fix/`와 같은 검증 절차를 사용합니다.
- 작업의 주된 목적에 따라 유형을 정합니다. 기능 구현에 필요한 테스트와 문서는 같은 기능 브랜치에 포함합니다.
- 한 브랜치·PR에는 독립적으로 배포 가능한 한 가지 목적을 담습니다. 같은 작업의 후속 수정은 해당 브랜치에서 이어갑니다.

PR CI가 검사하는 패턴은 다음과 같습니다.

```text
^(feature|fix|docs|refactor|test|ci|chore)/[a-z][a-z0-9]*(-[a-z0-9]+)*$
```

### 작업 시작부터 병합까지

1. 현재 브랜치와 미커밋 변경을 확인합니다. 기존 작업을 보존한 상태에서 원격 정보를 갱신하고 최신 `main`을 기준으로 작업 브랜치를 만듭니다.
2. 구현과 관련된 로컬 검증을 수행합니다. 에이전트의 커밋·push·병합 승인은 [AGENTS.md의 작업 승인 규칙](./AGENTS.md#작업-승인-규칙)을 따릅니다.
3. 작업 브랜치를 push하고 `main` 대상 PR을 만듭니다. PR에는 변경 이유, 변경 내용, 검증 결과를 기록합니다. 이슈 연결은 요구하지 않습니다.
4. 최신 `main`을 반영하고 필수 `PR CI`가 성공한 뒤 **Squash merge**로 병합합니다. 이미 push한 작업 브랜치에는 `main`을 merge하며, 공유 이력을 임의로 rebase하거나 force push하지 않습니다.
5. `main` 병합은 운영 CI/CD를 시작합니다. 배포 결과를 확인한 뒤 PR 병합 여부와 추가 미반영 작업이 없는지 확인하여 작업 브랜치를 정리합니다.
6. 병합한 브랜치는 재사용하지 않습니다. 다음 작업은 최신 `main`에서 새 브랜치로 시작합니다.

`main`은 관리자도 PR과 필수 CI를 거쳐야 하며, 강제 push와 브랜치 삭제가 차단됩니다. 리뷰어 승인 인원은 필수로 지정하지 않습니다. Squash merge만 허용하며, 작업별 변경은 main에 하나의 커밋으로 남습니다.

## 로컬 개발 시작하기

### 1. 사전 조건

- JDK 17
- MySQL
- Redis
- 저장소의 private config에 접근할 권한
- 소셜 로그인 검증 시 각 OAuth provider 설정과 Apple `AuthKey_382T2TB4RW.p8` 키
- 애플리케이션 시작용 `poppang.mail.*` property key
- 실제 신규 가입 메일 발송 검증 시 유효한 SMTP 설정

### 2. 저장소 받기

```bash
git clone https://github.com/team-PopPang/PopPang-BE-Prod.git
cd PopPang-BE-Prod
```

### 3. Private config 준비

`src/main/resources/application.yml`과 `application-prod.yml`은 비밀값이 없는 템플릿으로 커밋되어 있습니다. 다음 로컬 설정과 키는 `.gitignore` 대상이므로 별도로 준비합니다.

- `src/main/resources/application-local.yml` 등 로컬 private 설정
- Apple 로그인 키(`*.p8`)
- `.env`

Apple 키는 `*.p8` 패턴으로 제외합니다. 교체 키도 생성·다운로드·스테이징·사용 전에 `git check-ignore -v <키 경로>`로 제외 여부를 확인하세요.

기본 실행에는 MySQL, Redis와 아래 JWT 설정이 필요합니다.

- `jwt.secret`
- `jwt.access-token-exp-minutes`
- `jwt.refresh-token-exp-days`
- `jwt.issuer`

`EmailService`는 항상 Bean으로 등록되고 다음 값을 기본값 없이 주입합니다. 따라서 application context를 시작하려면 세 property key가 모두 존재해야 합니다.

- `poppang.mail.from`
- `poppang.mail.password`
- `poppang.mail.admins`

실제 메일 발송에는 유효한 SMTP 인증값과 관리자 수신 주소가 필요합니다. 안전한 `local` profile에서는 세 key를 정의한 뒤 값을 모두 비워 둘 수 있으며, 이 경우 `EmailService`의 설정 검사에서 발송을 건너뜁니다.

소셜 로그인 검증에는 OAuth provider와 Apple key 설정이 추가로 필요합니다. 비밀값을 README나 커밋, 채팅, 로그에 남기지 마세요. Private config 확보와 갱신 절차는 팀 관리자와 [DEPLOYMENT.md](./DEPLOYMENT.md)를 확인하세요.

> [!CAUTION]
> 애플리케이션의 기본 Spring profile은 `prod`입니다. 로컬 실행에서는 반드시 `local` profile을 명시하고, DB와 Redis가 로컬 개발 자원을 가리키는지 확인하세요. Gradle `test`는 별도의 `test` 프로필을 사용합니다.

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

Gradle `test`는 `test` 프로필과 전용 테스트 설정을 사용하며 운영 private config를 필요로 하지 않습니다. 테스트에 `prod` 프로필을 지정하면 실행 전에 실패합니다. 애플리케이션을 직접 실행하는 `bootRun`의 설정과 구분하세요.

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

- `main` 대상 PR의 필수 검사 이름은 `PR CI`입니다. 먼저 PR 원본 브랜치 이름을 검사하고, 규칙에 맞지 않으면 테스트·빌드 준비 전에 실패합니다.
- 브랜치 이름 검사를 통과하면 `./gradlew clean test spotlessCheck --no-daemon`으로 테스트와 포맷을 검사합니다. private 설정을 다운로드하지 않습니다.
- PR CI의 수동 실행(`workflow_dispatch`)에서는 브랜치 이름 검사를 건너뛰고 같은 테스트·포맷 검사를 수행하므로 `main`에서도 실행할 수 있습니다.
- `main` push와 `main`에서의 운영 워크플로 수동 실행은 `Main Verify`로 같은 테스트·포맷 검사를 수행합니다. 성공한 커밋만 JAR·Docker 이미지 생성, 산출물 검사, 운영 서버 전송·배포로 이어집니다.
- 운영 이미지는 `poppang-prod:<커밋 SHA 앞 7자리>`입니다. 운영 설정과 Apple 키는 서버 런타임에서 주입하며, 배포 스크립트가 헬스 체크와 실패 시 롤백을 담당합니다.
- 현재 문서만 변경한 PR도 `main`에 병합하면 운영 재배포가 실행됩니다.
- 로컬 `make`의 기본 동작은 도움말이며 `build-jar`, `build-image`, `save-image`는 로컬 산출물 생성용입니다. `prod-deploy` 등 수동 운영 배포 경로는 차단되어 있습니다.

운영 절차는 [DEPLOYMENT.md](./DEPLOYMENT.md)와 [CI/CD 안전성 스펙](./docs/specs/ci-cd-safety.md)을 참고하세요. 배포 런북에는 이전 미니 PC·수동 배포 설명이 남아 있으므로 현재 워크플로와 AWS 이전 계약에 맞는지 확인해야 합니다.

## 개발 문서

- [AGENTS.md](./AGENTS.md): 코드 구조, 구현 규칙, 보안, 주요 함정
- [DEPLOYMENT.md](./DEPLOYMENT.md): 배포, 검증, 롤백, 운영 위험
