# PopPang BE CI/CD production safety

## Status

`APPROVED` — 2026-07-18

## Goal

JWT v2를 여러 청크로 추가하는 동안 기존 v1 서비스를 유지하면서, 검증되지 않은 코드가 단일 운영
서버에 바로 배포되는 경로를 막는다. 복잡한 배포 플랫폼을 새로 도입하지 않고 현재 GitHub Actions,
Docker tar 전송, 원격 `deploy-prod.sh` 구조를 최소한으로 보강한다.

## Scope

- `main` 대상 PR의 필수 CI 검증
- `main` 병합 후 운영 자동 배포 전 재검증
- 운영 Docker 이미지·컨테이너 명칭 정합성
- 단일 운영 배포 동시성 제어
- 배포 후 health check와 실패 시 직전 이미지 자동 롤백
- 이메일 알림 실패와 실제 배포 결과의 분리
- JWT v2 청크별 PR·병합·배포 원칙
- GitHub `main` branch protection의 최소 운영 규칙

## Non-goals

- staging 서버 신규 구축
- Blue/Green, Canary, Kubernetes 또는 완전한 무중단 배포
- 테스트 커버리지 임계값과 전체 API E2E 테스트
- 모든 PR에서 성능 테스트 실행
- CI/CD에서 DDL 또는 migration SQL 실행
- 외부 운영·개발 DB/Redis를 사용하는 CI 테스트
- 원격 `deploy-prod.sh`의 secret을 환경 파일이나 secret manager로 이전
- 현재 평문으로 관리되는 운영 credential 교체
- 원격 `deploy-prod.sh` 자체 수정

원격 스크립트의 secret 관리와 credential 교체는 위험이 남아 있음을 인지하고 별도 후속 작업으로
진행한다. 실제 값은 Workflow, 저장소 문서, 로그에 복사하지 않는다.

## Current state

### PR CI

- `.github/workflows/build-test.yml`은 `main` 대상 PR에서 실행된다.
- private repository에서 공통·운영 설정을 다운로드한 뒤 `./gradlew clean build`를 실행한다.
- 명시적인 test profile과 외부 DB/Redis 차단 계약이 없다.

### Main CD

- `.github/workflows/cicd.yml`은 `main` push와 수동 실행에서 동작한다.
- 테스트 없이 `./gradlew clean bootJar`가 성공하면 Docker 이미지 생성과 운영 배포가 이어진다.
- Workflow 생성 시점부터 이미지 이름은 `poppang-dev:<short-sha>`였지만 호출 스크립트는
  `deploy-prod.sh`였다.
- 실제 운영 컨테이너는 `poppang-prod`, 외부 포트는 `4002`, 내부 포트는 `8080`, active profile은
  `prod`다. 별도 개발 컨테이너 `poppang-dev`는 외부 포트 `4003`에서 실행된다.
- 현재 `deploy-prod.sh`는 기존 `poppang-prod` 컨테이너를 제거한 뒤 신규 컨테이너를 실행하며,
  health check와 롤백을 수행하지 않는다.

따라서 지금까지 운영은 `deploy-dev.sh`가 아니라 `deploy-prod.sh`로 배포됐고, 운영 이미지의
repository 이름만 `poppang-dev`로 잘못 지정된 상태다.

## Target flow

```text
JWT v2 청크별 PR
  -> PR CI: clean test + spotlessCheck
  -> 필수 CI 통과
  -> main 병합
  -> Main verify: 동일 검증 재실행
  -> private 운영 설정 다운로드
  -> bootJar + poppang-prod:<short-sha> 이미지 생성
  -> 운영 서버 전송
  -> 기존 이미지 기록
  -> deploy-prod.sh 실행
  -> health check
       -> 성공: 배포 성공
       -> 실패: 직전 이미지 자동 재배포 후 Workflow 실패
```

## Implementation execution protocol

이 문서 하나를 설계와 구현 진행 상태의 기준으로 사용한다. 별도의 구현 계획 문서는 만들지 않는다.
작업 브랜치는 `feature/ci-cd-safety-refactor`다.

각 청크의 상태는 다음 순서로만 변경한다.

```text
TODO -> IN_PROGRESS -> AWAITING_CONFIRMATION -> CONFIRMED
```

- 한 번에 하나의 청크만 `IN_PROGRESS`로 둔다.
- 구현과 해당 검증이 끝나면 상태를 `AWAITING_CONFIRMATION`으로 바꾸고 작업을 멈춘다.
- 변경 파일, 실행한 검증, 결과, 남은 위험을 사용자에게 보고한다.
- 사용자가 결과를 컨펌하기 전에는 다음 청크를 시작하지 않는다.
- 사용자 컨펌 후 현재 청크를 `CONFIRMED`로 바꾸고 다음 청크를 시작한다.
- 청크 진행 컨펌은 git commit 또는 push 승인으로 간주하지 않는다. commit 직전에는 대상 파일과 예정
  메시지를 알리고 별도 승인을 받으며, push도 다시 별도 승인을 받는다.
- 검증이 실패하면 해당 청크를 `IN_PROGRESS`로 유지한다. 같은 검증의 추측성 반복 대신 원인과 남은
  작업을 보고한다.

## Implementation chunks

### Chunk 1. Preflight and safety baseline

Status: `CONFIRMED`

목표는 외부 상태를 변경하지 않고 구현과 롤백에 필요한 전제 조건을 확인하는 것이다.

- [x] 현재 Workflow, 테스트, private config 의존성과 위험 경로를 다시 확인한다.
- [x] 전체 테스트를 안전하게 격리하기 전에는 현재 `@SpringBootTest` 전체 suite를 실행하지 않는다.
- [x] GitHub `main` branch protection의 현재 상태와 설정 권한을 읽기 전용으로 확인한다.
- [x] 운영 서버에서 `curl`, Docker daemon, Docker image 저장 공간을 읽기 전용으로 확인한다.
- [x] 현재 `poppang-prod` 컨테이너의 image name과 image 존재 여부를 credential 출력 없이 확인한다.

검증과 완료 조건:

- 외부 DB/Redis, 배포, branch protection 변경 없이 확인 결과가 정리돼야 한다.
- 구현에 필요한 명령이나 권한이 부족하면 다음 청크로 넘어가기 전에 blocker로 보고해야 한다.

컨펌 게이트: 사전 확인 결과와 현재 위험을 보고한 뒤 사용자 컨펌을 기다린다.

사전 확인 결과 — 2026-07-18:

- PR CI가 private `application.yml`, `application-prod.yml`을 다운로드한 뒤 `clean build`를 실행하고,
  `PoppangBeApplicationTests`는 별도 test profile 없이 전체 Spring context를 로드한다. 외부 endpoint가
  있는 로컬 private config도 확인돼 전체 test suite는 실행하지 않았다.
- Main CD는 `verify`, production concurrency, health check, rollback이 없고 운영 컨테이너에
  `poppang-dev:18af621` 이미지를 사용 중이다.
- GitHub `main`에는 branch protection이나 적용 중인 branch ruleset이 없다. 현재 로컬 `gh` 인증
  계정은 repository admin 권한이 있어 이후 보호 규칙을 적용할 수 있다.
- 운영 서버는 `curl 8.5.0`, Docker server `28.5.1`을 사용할 수 있다. `deploy-prod.sh`는 존재하고
  실행 가능하며 deploy 디렉터리도 현재 사용자에게 쓰기 가능하다.
- Docker와 deploy 디렉터리가 있는 filesystem은 약 23.4 GiB가 남았지만 사용률이 92%다. Docker
  image는 280개이고 약 26.62 GB가 reclaimable하므로 즉시 blocker는 아니지만 배포 전후 용량 감시가
  필요하다. 이 청크에서는 image 삭제나 정리를 수행하지 않았다.
- `poppang-prod` 컨테이너는 실행 중이고 `4002 -> 8080` port mapping과 현재 image 존재를 확인했다.
  현재 image 크기는 약 476 MiB이므로 직전 image tar와 신규 image를 둘 여유는 확인됐다.
- health endpoint는 서버 localhost와 외부 네트워크에서 HTTP 200, `UP`이다. 서버에서 공개 도메인
  주소로 되돌아가는 요청만 timeout이므로 공개 URL health check는 외부 GitHub runner에서 수행하고,
  원격 서버 내부 확인이 필요하면 localhost를 사용해야 한다. 실제 GitHub runner 경로는 PR/배포 전
  Workflow 실행에서 다시 확인한다.
- 외부 DB/Redis 연결, 배포, branch protection 변경, credential 출력은 수행하지 않았다. 다음 청크를
  막는 blocker는 없으며 디스크 사용률과 서버 내부 공개 URL timeout을 추적 위험으로 남긴다.

### Chunk 2. Isolated test runtime

Status: `CONFIRMED`

목표는 private 운영 설정과 외부 DB/Redis 없이 필수 테스트 명령을 안전하게 실행할 수 있게 만드는
것이다.

- [x] 현재 위험을 재현하거나 격리 계약을 검증할 focused test를 먼저 추가한다.
- [x] 명시적인 test profile 또는 격리된 test application을 적용한다.
- [x] 필요하지 않은 테스트에서 DataSource, JPA, Redis auto-configuration을 끈다.
- [x] `PoppangBeApplicationTests`가 기본 `prod` 설정이나 외부 endpoint에 의존하지 않게 한다.
- [x] 운영 설정 파일과 Apple 로그인 키를 생성하거나 다운로드하지 않고 테스트한다.

검증과 완료 조건:

```bash
./gradlew clean test spotlessCheck --no-daemon
```

- 위 명령이 private config 없이 성공해야 한다.
- 테스트 로그와 설정에 운영·개발 DB/Redis 접속 시도가 없어야 한다.
- 운영 profile과 운영 resource 파일은 수정하지 않아야 한다.

컨펌 게이트: 테스트 격리 방식, 실행 결과, 외부 연결 차단 근거를 보고한 뒤 사용자 컨펌을 기다린다.

격리 및 검증 결과 — 2026-07-18:

- TDD RED 단계에서 Spring context를 띄우지 않는 `TestRuntimeIsolationContractTest`를 먼저 추가했다.
  기존 test task에서는 `spring.profiles.active`가 `null`이라 의도대로 실패했다.
- Gradle `test` task가 `spring.profiles.active=test`와
  `spring.config.location=classpath:/application-test.yml`을 고정한다. 환경변수나 JVM 속성으로 `prod`
  profile을 요청하면 테스트 실행 전에 실패한다.
- `application-test.yml`은 DataSource, DataSource transaction manager, Hibernate JPA, JPA repository,
  Redis, Redis repository auto-configuration을 제외하고 SQL 초기화와 scheduling도 끈다.
- `PoppangBeApplicationTests`는 production component scan을 하지 않는 최소 `TestApplication`을 사용한다.
  active profile이 `test` 하나뿐이고 test config만 property source에 있으며 DataSource,
  `EntityManagerFactory`, `RedisConnectionFactory`, `RedisTemplate` bean이 없음을 검증한다.
- Spring context를 사용하는 기존 OpenAPI와 Web MVC 테스트에도 `test` profile과 test config location을
  명시했다. private 기본·prod·dev·local 설정은 property source에 포함되지 않는다.
- 문서용 비라우팅 주소를 DB와 Redis 환경변수에 함께 주입한 음성 검증은 연결 오류 없이
  `spring.datasource.url`, `spring.data.redis.host`를 안전 계약 위반으로 검출해 예상대로 실패했다.
- 격리 후 `./gradlew clean test spotlessCheck --no-daemon`을 실행해 72개 테스트와 Spotless 검사를
  통과했다. 기존 `PopupWebController.java` 마지막의 불필요한 빈 줄 한 개는 동작 변경 없이 제거했다.
- 로컬 private 설정과 Apple 키는 삭제·이동·수정하지 않았고 새 private 파일도 다운로드하지 않았다.
  운영 서버, GitHub 설정, branch protection을 조회하거나 변경하지 않았다.
- 원본 private 파일은 그대로 둔 채 `/private/tmp` 작업 사본에서 `application*.yml`과 Apple `.p8`을
  제외하고 동일한 필수 Gradle 명령을 다시 실행해 성공했다. 임시 사본은 검증 후 삭제했다. 고정된
  config location, property source 검사, infrastructure bean 부재와 함께 현재 테스트가 private 파일에
  의존하지 않음을 검증했다. 향후 테스트가 auto-configuration을 직접 다시 활성화하지 않도록 이
  계약을 유지해야 한다.

### Chunk 3. v1 compatibility contracts

Status: `CONFIRMED`

목표는 JWT v2 작업 중 기존 v1 endpoint와 익명 접근 동작이 우발적으로 바뀌지 않게 고정하는 것이다.

- [x] 기존 v1 controller의 HTTP method와 path inventory를 계약 테스트로 고정한다.
- [x] 기존 v1 요청이 인증 토큰 없이 Security filter chain을 통과하는 동작을 계약 테스트로 고정한다.
- [x] 기존 OpenAPI media type 계약 테스트와 중복되지 않게 필요한 범위만 추가한다.
- [x] 전체 API E2E 환경이나 외부 DB/Redis 의존성을 만들지 않는다.
- [x] v1 production code의 path, 요청, 응답 또는 보안 동작은 변경하지 않는다.

검증과 완료 조건:

- 새 focused contract test와 전체 `test` task가 성공해야 한다.
- v1 endpoint가 제거·변경되거나 전역 인증 정책이 바뀌면 새 테스트가 실패해야 한다.

컨펌 게이트: 고정한 endpoint 범위와 익명 접근 검증 근거를 보고한 뒤 사용자 컨펌을 기다린다.

v1 호환성 계약 결과 — 2026-07-18:

- 애플리케이션 domain의 `@RestController` 14개에서 현재 `/api/v1/**` HTTP method와 전체 path 78개를
  승인 inventory로 명시했다. 테스트 실행 중 얻은 목록을 기대값으로 재사용하지 않고 Java `Set.of`에
  78개를 직접 기록했다.
- 실제 목록은 classpath의 애플리케이션 `@RestController`와 합성된 `@RequestMapping` 메타데이터에서
  계산한다. 고정 inventory와 exact set 비교를 수행하므로 endpoint 추가·삭제, HTTP method 변경,
  전체 path 또는 path variable 이름 변경 시 계약 테스트가 실패한다.
- Swagger `@Hidden`이 붙은 auth/token 12개, popup 2개, user 2개를 포함해 hidden endpoint 16개도
  inventory에 포함했다. OpenAPI 노출 여부는 endpoint 호환 대상 선정에 사용하지 않았다.
- Actuator, 기본·custom Swagger, `/error` 같은 framework endpoint는 application domain controller가
  아니며 `/api/v1/**` 대상도 아니어서 제외했다. `GlobalExceptionHandler`는
  `@RestControllerAdvice`이고 독립 request mapping이 없어 제외했다.
- 익명 접근 테스트는 승인된 78개 endpoint 각각의 path variable을 테스트 값으로 치환하고 실제
  `SecurityConfig`와 `JwtAuthenticationFilter`를 통과시킨다. DispatcherServlet 대신 테스트 종단
  filter chain이 HTTP 204를 설정하며, 모든 요청이 종단에 도달하고 401·403이 아니며 JWT provider와
  user repository를 호출하지 않음을 검증한다.
- 익명 접근 계약은 URL Security filter chain의 차단 여부만 보장한다. controller argument binding,
  method security, request validation, service 결과, status·body·media type 같은 비즈니스 응답은
  호출하거나 중복 검증하지 않는다.
- 새 focused contract test와 Chunk 2 격리 테스트를 함께 실행해 성공했다. 마지막으로
  `./gradlew clean test spotlessCheck --no-daemon`을 새로 실행해 74개 테스트, 실패 0개, 오류 0개와
  Spotless 검사를 통과했다.
- 새 계약용 TestApplication도 `test` profile과 `application-test.yml`을 사용하고 DataSource, JPA,
  Redis auto-configuration을 제외한다. 전체 test 결과에서 외부 DB/Redis 연결 흔적은 확인되지 않았다.
- 이 청크에서는 v1 production controller, path, method, 요청·응답, `SecurityConfig`를 수정하지 않았다.
  추가한 파일은 계약 테스트 하나이며 기존 OpenAPI media type 테스트도 변경하지 않았다.
- 남은 위험은 이 계약이 Spring MVC annotation 기반 controller만 inventory로 삼는다는 점이다. 현재는
  functional route가 없으며, 추후 도입한다면 별도 inventory 수집 계약이 필요하다.

### Chunk 4. PR CI gate

Status: `CONFIRMED`

목표는 `main` 대상 PR을 운영 credential과 외부 서비스 없이 검증하는 것이다.

- [x] `.github/workflows/build-test.yml` 권한을 `contents: read`로 제한한다.
- [x] private repository 다운로드와 운영 secret 참조를 제거한다.
- [x] `main` 대상 `pull_request`에서 실행되게 유지한다.
- [x] 검증 명령을 `./gradlew clean test spotlessCheck --no-daemon`으로 고정한다.
- [x] Chunk 2와 Chunk 3의 테스트가 일반 `test` task에 포함되는지 확인한다.

검증과 완료 조건:

- Workflow 문법과 trigger를 정적 검증해야 한다.
- PR Workflow에 private config 다운로드, 외부 DB/Redis 주소, 운영 credential 참조가 없어야 한다.
- 로컬에서 Workflow와 동일한 Gradle 명령이 성공해야 한다.

컨펌 게이트: PR Workflow diff와 동일 명령 실행 결과를 보고한 뒤 사용자 컨펌을 기다린다.

PR CI gate 결과 — 2026-07-18:

- Workflow 계약 테스트를 먼저 추가했다. 변경 전 focused 실행은 3개 테스트가 모두 실패해 명시적인
  Workflow 권한 부재, 운영 전역 환경변수와 private 설정 다운로드 존재, 고정 Workflow·job 이름 및
  검증 명령 불일치를 RED로 확인했다.
- `.github/workflows/build-test.yml`은 Workflow 이름을 `PopPang BE PR CI`, job id를 `pr-ci`, 명시적인
  job 이름을 `PR CI`로 고정했다. 향후 branch protection에서 사용할 status check 이름은 `PR CI`이며,
  Actions 화면에는 `PopPang BE PR CI / PR CI` 조합으로 표시될 수 있다.
- 최상위 권한은 `contents: read` 하나만 선언했고 job 수준 권한 재정의는 두지 않았다. 자동 trigger는
  `main` 대상 `pull_request`를 그대로 유지했으며 기존 수동 `workflow_dispatch`도 유지했다. 새 trigger는
  추가하지 않았다.
- `PRIVATE_BASE_URL`, `PERSONAL_ACCESS_TOKEN`, private repository 설정 다운로드 step과 `curl`을
  제거했다. Workflow에는 secret 표현식, `application.yml`, `application-prod.yml`, Apple `.p8`, JDBC,
  MySQL 또는 Redis 주소·설정 marker가 없음을 계약 테스트와 별도 `rg` 검색으로 확인했다.
- 검증 step은 `./gradlew clean test spotlessCheck --no-daemon` 한 개로 고정했다. Workflow 수정 후
  focused 계약 테스트 3개가 모두 통과했다.
- SnakeYAML 기반 계약 테스트와 로컬 Ruby/Psych로 YAML을 파싱해 trigger가
  `pull_request(main)`과 기존 `workflow_dispatch`뿐이고 권한이 `contents: read`임을 정적 검증했다.
  로컬에는 `actionlint`, `yq`, `yamllint`가 없어 GitHub Actions 전용 schema 검사는 실행하지 못했다.
- 첫 전체 검증에서는 테스트 task가 통과한 뒤 새 계약 테스트의 한 줄 포맷 차이로 Spotless가
  실패했다. 해당 포맷만 수정하고 최종 Workflow 기준으로
  `./gradlew clean test spotlessCheck --no-daemon`을 다시 실행해 77개 테스트, 실패·오류·건너뜀 0개와
  Spotless 검사를 통과했다.
- 전체 test 결과 XML에 Chunk 2 `TestRuntimeIsolationContractTest` 1개, Chunk 3
  `V1ApiCompatibilityContractTest` 2개, Chunk 4 `PrCiWorkflowContractTest` 3개가 모두 존재해 일반
  `test` task 포함을 확인했다.
- `.github/workflows/cicd.yml`, production code, 운영 서버, GitHub 설정과 branch protection은 이
  청크에서 변경하지 않았다. 로컬 private 설정 두 개와 Apple 키도 원래 경로에 그대로 존재한다.
- 실제 GitHub-hosted runner에서 action checkout/setup, Gradle dependency cache·다운로드, check 생성이
  성공하는지는 아직 확인하지 않았다. 따라서 `PR CI`가 GitHub의 required status check 선택지에 실제로
  나타나는지는 향후 feature branch push와 PR 실행 뒤 확인해야 한다.

### Chunk 5. Main verify and production build gate

Status: `CONFIRMED`

목표는 동일 commit의 재검증이 성공한 뒤에만 운영용 산출물을 만들 수 있게 하는 것이다.

- [x] `.github/workflows/cicd.yml`에 private config를 사용하지 않는 `verify` job을 추가한다.
- [x] `verify`에서 PR CI와 동일한 Gradle 명령을 실행한다.
- [x] 이미지 생성·전송·배포 job을 `needs: verify`로 연결한다.
- [x] `workflow_dispatch`가 `main` revision에서만 운영 배포되도록 guard를 추가한다.
- [x] 운영 이미지 repository를 `poppang-prod`로 통일하고 short SHA tag를 유지한다.
- [x] private config와 Apple 키 다운로드를 CD build job 안에서만 수행한다.
- [x] 다운로드의 HTTP 오류, 빈 파일, HTML, `Not Found` 응답을 실패 처리한다.

검증과 완료 조건:

- `verify` 실패 또는 취소 경로에서 secret 다운로드, Docker build, 전송, 배포가 실행될 수 없어야 한다.
- 검증 commit과 이미지에 포함되는 commit이 같아야 한다.
- 로그에 다운로드 credential이나 private file 내용이 출력되지 않아야 한다.

컨펌 게이트: job 의존 관계와 다운로드 안전성 검증 결과를 보고한 뒤 사용자 컨펌을 기다린다.

Main verify와 production build gate 결과 — 2026-07-18:

- Main Workflow 계약 테스트를 먼저 추가했다. 변경 전 focused 실행은 5개 테스트가 모두 실패해
  독립 `verify` job, main 수동 실행 guard, `needs: verify`, 동일 SHA checkout, production 이름과
  다운로드 sanity check가 없는 기존 위험을 RED로 확인했다.
- `verify` job은 운영 env나 secret 없이 `${{ github.sha }}`를 checkout하고
  `./gradlew clean test spotlessCheck --no-daemon`만 검증 명령으로 실행한다. `main` push이거나
  `workflow_dispatch`에서 `refs/heads/main`을 선택한 경우에만 실행하는 guard를 둔다.
- `build-and-deploy`는 `needs: verify`와 `if: needs.verify.result == 'success'`를 모두 사용한다. 따라서
  verify 실패·취소·skip 시 private 설정 다운로드, JAR와 Docker image 생성, tar 저장, 서버 전송과
  원격 배포가 시작되지 않는다.
- mail secret을 사용하는 `notify`도 `verify`와 `build-and-deploy`를 직접 필요로 하고
  `always() && needs.verify.result == 'success'`일 때만 실행한다. verify가 성공하기 전에는 Workflow의
  어떤 secret 사용 job도 실행될 수 없도록 전역 `env`를 제거하고 job별로 격리했다.
- 자동 trigger는 기존 `main` push를 유지하고 기존 수동 `workflow_dispatch` 외 trigger는 추가하지
  않았다. 다른 branch나 tag를 수동 선택하면 verify가 skip되고 모든 후속 job도 차단된다.
- verify와 build job 모두 `${{ github.sha }}`를 명시적으로 checkout한다. image tag는 같은 실행의
  `GITHUB_SHA` 앞 7자를 사용해 `poppang-prod:<short-sha>`로 만들며 tar와 원격 배포에도 같은
  `IMAGE_NAME`을 전달한다.
- image repository용 `APP_NAME`과 운영 `CONTAINER_NAME`을 모두 `poppang-prod`로 설정했다. 원격
  `deploy-prod.sh`에도 두 값을 환경변수로 전달하지만, 서버의 script가 저장소 밖에 있어 실제 container
  생성 시 이 값을 따르는지는 운영 배포 전 별도 확인이 필요하다.
- verify 성공 뒤 `build-and-deploy` 안에서만 private `application.yml`, `application-prod.yml`, Apple
  `AuthKey_382T2TB4RW.p8`을 받는다. 기존 production build에 불필요한 dev/local 설정 다운로드는
  제거했다. `set -euo pipefail`과 `curl --fail --silent --show-error --location`으로 HTTP 오류를
  실패 처리하고, 각 파일에 non-empty 검사와 case-insensitive HTML·`Not Found` 검사를 적용한다.
  검사에는 내용을 출력하는 명령이 없고 token은 secret 환경변수로만 참조한다.
- Workflow 수정 후 focused 계약 테스트 5개가 모두 통과했다. Ruby/Psych YAML 파싱으로 trigger와
  `verify -> build-and-deploy -> notify` 그래프를 재확인했고, 모든 로컬 run block과 표현식을 치환한
  원격 script가 실행 없는 Bash 문법 검사를 통과했다. 로컬에는 `actionlint`, `yq`, `yamllint`가 없어
  GitHub Actions 전용 schema 검사는 실행하지 못했다.
- 첫 전체 검증에서는 전체 test task가 통과한 뒤 새 계약 테스트 한 줄의 포맷 차이로 Spotless가
  실패했다. 해당 포맷만 수정한 뒤 `./gradlew clean test spotlessCheck --no-daemon`을 다시 실행해
  82개 테스트, 실패·오류·건너뜀 0개와 Spotless 검사를 통과했다.
- `.github/workflows/build-test.yml`, production Java 코드, 기존 테스트 격리·v1 계약은 이 청크에서
  변경하지 않았다. 운영 서버, GitHub 설정과 branch protection을 조회·변경하지 않았고 Docker
  build·전송·원격 배포도 실행하지 않았다. 로컬 private 설정과 Apple 키는 기존 위치에 보존했다.
- 실제 GitHub-hosted runner의 조건식 평가, action과 cache 동작, secret 가용성, private 응답의 실제
  형식, image 생성·전송 및 원격 script 결과는 아직 확인하지 않았다. production 직렬화, health check,
  rollback과 notification 비차단화는 각각 다음 승인 청크의 범위로 남겨뒀다.

### Chunk 6. Serialized deployment, health check, and rollback

Status: `CONFIRMED`

목표는 production 배포를 직렬화하고 신규 버전이 healthy하지 않을 때 직전 이미지를 자동 복구하는
것이다.

- [x] production concurrency group을 추가하고 `cancel-in-progress: false`로 설정한다.
- [x] 배포 전에 현재 `poppang-prod` 컨테이너의 image name과 image 존재 여부를 검증한다.
- [x] 직전 이미지가 있으면 원격 서버에 rollback tar를 생성한다.
- [x] 기존 원격 `deploy-prod.sh`를 수정하지 않고 신규 tar와 image name으로 배포한다.
- [x] health endpoint의 HTTP 성공과 응답 `UP`을 최대 60초 동안 재시도한다.
- [x] 신규 배포가 unhealthy하면 rollback tar와 직전 image name으로 `deploy-prod.sh`를 다시 호출한다.
- [x] 롤백 후 health check를 다시 실행하고 성공 여부를 구분해 기록한다.
- [x] 롤백이 성공해도 신규 배포 Workflow는 실패로 종료한다.
- [x] 직전 이미지가 없거나 롤백까지 실패하면 수동 복구가 필요함을 명확히 표시한다.
- [x] 임시 tar 정리와 로그 범위를 image name, commit SHA, health 결과로 제한한다.

검증과 완료 조건:

- 가능한 로컬 shell 문법 검사와 실패 경로 dry run 또는 테스트를 수행한다.
- 성공, 신규 배포 실패·롤백 성공, 신규 배포 실패·롤백 실패, 직전 이미지 없음 경로를 각각
  검토해야 한다.
- 실제 production 배포는 이 청크의 로컬 검증에 포함하지 않는다.

컨펌 게이트: 네 가지 배포 경로와 검증 한계를 보고한 뒤 사용자 컨펌을 기다린다.

직렬 배포·health check·rollback 결과 — 2026-07-18:

- Workflow와 네 dry-run 경로의 계약 테스트를 먼저 추가했다. 변경 전 focused 실행은 7개 테스트가
  모두 실패해 concurrency와 rollback helper 부재를 RED로 확인했다. 구현 중 HTTP 2xx 판정 강화는
  3개 실패, 다중 pending queue 계약은 1개 실패로 각각 RED를 다시 확인한 뒤 GREEN으로 전환했다.
- Workflow 최상위 concurrency는 `poppang-production-deployment`, `cancel-in-progress: false`,
  `queue: max`로 고정했다. 실행 중인 production Workflow를 새 실행이 취소하지 않고 최대 100개
  pending 실행이 같은 group에서 대기한다. 대기 시작 시각 기준 처리이므로 dispatch 시각과 순서가
  항상 같다는 보장은 없다.
- Chunk 5의 `verify -> build-and-deploy -> notify` graph, verify 명령·test 격리·`${{ github.sha }}`
  checkout·main revision guard는 유지했다. rollback helper 복사와 실행은 `build-and-deploy` 안에 있어
  verify 실패·취소 시 접근할 수 없다.
- 테스트 가능한 `production-deploy-with-rollback.sh`을 신규 image tar와 함께 서버로 복사한다. 배포
  직전 `docker inspect --type container --format '{{.Config.Image}}' poppang-prod`로 정확한 직전 image
  name과 tag를 읽고 `docker image inspect`로 daemon 존재를 확인한다.
- 직전 image가 있으면 `/home/poppang/opt/deploy/rollback/poppang-prod-rollback-<run-id>-<attempt>.tar`에
  `docker save`한다. backup 실패 시 신규 배포 전에 실패·수동 복구 필요로 종료한다. 기존 원격
  `/home/poppang/opt/deploy/deploy-prod.sh`는 수정하지 않고 신규와 rollback 모두 tar와 image name 두
  인자로 같은 script를 호출한다.
- health endpoint는 원격 운영 서버 내부에서 접근 가능한 `http://localhost:4002/actuator/health`로
  고정했다. 새 배포와 rollback 각각 최대 60초, 최대 12회, 기본 5초 간격으로 확인하고, 각 요청
  timeout은 최대 4초이면서 남은 전체 시간보다 길지 않게 제한한다. curl 성공 뒤 실제 HTTP code가
  2xx이고 `jq`의
  `.status == "UP"` 판정이 참일 때만 healthy다. 응답 본문은 로그에 출력하지 않는다.
- 실행 없는 deterministic dry run은 PATH에 `docker`, `curl`, `jq`, `sleep`, `rm` stub과 임시
  `deploy-prod.sh`를 주입했다. 실제 SSH, Docker daemon, health endpoint, 운영 서버에 접근하지 않았고
  image나 tar를 만들거나 삭제하지 않았다.
- 네 경로 결과는 다음과 같다.
  - 신규 배포 health `UP`: 신규 deploy 1회, exit 0, Workflow 성공.
  - 신규 health 실패·rollback health `UP`: 신규와 직전 image deploy 각 1회, rollback 성공을 남긴 뒤
    exit 1, 실패한 신규 release이므로 Workflow 실패.
  - 신규 health 실패·rollback도 unhealthy: 두 deploy 후 `rollback_result=failed`와
    `manual_recovery=required`, exit 1.
  - 직전 image 없음·신규 health 실패: rollback 호출 없이 `rollback_result=unavailable`과
    `manual_recovery=required`, exit 1.
- rollback tar는 run id와 attempt로 고유하게 만들고 EXIT trap에서 rollback directory 아래의
  `poppang-prod-rollback-*.tar` 한 파일에만 `rm -f --`를 허용한다. prune, image 삭제, 광범위한 rm은
  없다. 외부 command와 기존 deploy script 출력은 숨기고 commit SHA, 신규·직전 image, health,
  rollback과 manual recovery 결과만 구조화해 출력한다.
- `bash -n`, 모든 Workflow run block과 표현식을 치환한 원격 script의 Bash 문법 검사, Ruby/Psych
  YAML·trigger·concurrency·dependency graph 검사, 광범위 삭제·credential 출력·migration·외부
  DB/Redis 검색이 모두 통과했다. `actionlint`와 `shellcheck`는 로컬에 없어 실행하지 못했다.
- 첫 전체 검증은 test task 통과 후 새 테스트의 Google Java Format 차이로 Spotless가 실패했다.
  해당 새 파일만 포맷한 뒤 `./gradlew clean test spotlessCheck --no-daemon`을 다시 실행해 89개 테스트,
  실패·오류·건너뜀 0개와 Spotless 검사를 통과했다.
- `.github/workflows/build-test.yml`, production Java 코드, Chunk 5 verify gate와 notify 조건은 변경하지
  않았다. 로컬 private 설정·Apple 키, 원격 `deploy-prod.sh`, 운영 서버, GitHub 설정과 branch
  protection도 변경하지 않았고 실제 build·전송·배포·SSH·health 요청을 실행하지 않았다.
- 실제 GitHub runner에서 `queue: max`, scp 경로 보존, SSH action exit code 전파가 의도대로 동작하는지,
  원격 서버에 `docker`·`curl`·`jq`·`bash`가 있는지, 기존 `deploy-prod.sh`가 전달한 image와 container
  계약을 지키는지는 아직 확인하지 않았다. rollback 실패, 직전 image 부재, backup/cleanup 실패,
  필수 command 부재는 즉시 수동 복구가 필요한 조건이다.

### Chunk 7. Deployment result and non-blocking notification

Status: `CONFIRMED`

목표는 실제 배포 결과와 보조 이메일 알림 결과를 분리하고 실패 원인을 항상 남기는 것이다.

- [x] GitHub Actions job 결과를 build·test·deploy 성공 여부의 기준으로 유지한다.
- [x] 이메일 step 또는 job을 non-blocking으로 설정한다.
- [x] 이메일 실패가 성공한 배포를 실패로 바꾸지 않게 한다.
- [x] 배포 실패와 롤백 실패를 `$GITHUB_STEP_SUMMARY`에 항상 기록한다.
- [x] 성공, 배포 실패, 롤백 성공, 롤백 실패를 summary에서 구분한다.

검증과 완료 조건:

- 알림 실패를 가정해도 build·test·deploy 결과가 보존되는지 Workflow 조건식을 검토해야 한다.
- 실패 summary가 credential 없이 원인과 수동 복구 필요 여부를 보여야 한다.

컨펌 게이트: 결과 판정표와 summary·알림 조건을 보고한 뒤 사용자 컨펌을 기다린다.

배포 결과·비차단 알림 결과 — 2026-07-18:

- Workflow와 summary helper의 focused 계약 테스트 6개를 먼저 추가했다. 변경 전 실행은 6개가 모두
  실패해 SSH stdout 미캡처, summary helper 부재, 이메일 step의 blocking 상태를 RED로 확인했고,
  최소 구현 후 6개 모두 GREEN으로 전환했다.
- `Remote deploy`는 `id: remote_deploy`, `continue-on-error: true`로 결과 보고 step까지 진행할 수 있게
  했다. stdout output 계약이 없는 `appleboy/ssh-action@v1.0.3`을 `v1.2.5`로 갱신하고
  `capture_stdout: true`를 사용한다. 원격 helper의 실제 exit code는 `remote_exit_code=<code>`로
  구조화해 캡처하고 SSH wrapper는 0으로 끝낸다. SSH transport/action 자체가 실패하면
  `steps.remote_deploy.outcome`이 `failure`로 유지된다.
- 마지막 `Report deployment result` step은 `if: always()`로 실행된다. action의 원래 outcome과 캡처한
  `remote_exit_code`, `deployment_result`, health·rollback marker를 함께 판정해
  `$GITHUB_STEP_SUMMARY`를 먼저 작성한 뒤 성공한 신규 배포만 0, 나머지는 1로 종료한다. 따라서
  rollback 성공도 실패한 신규 release를 성공으로 바꾸지 않는다.
- summary 판정은 다음과 같다.
  - 신규 health `UP`·helper exit 0: `Deployment=SUCCESS`, `Rollback=NOT_REQUIRED`, 수동 복구 불필요,
    `build-and-deploy` 성공.
  - 신규 배포 실패·rollback 성공: `Deployment=FAILED`, `Rollback=SUCCESS`, 직전 image 복구를 표시한 뒤
    `build-and-deploy` 실패.
  - 신규 배포 실패·rollback 실패: `Deployment=FAILED`, `Rollback=FAILED`, 즉시 수동 복구 필요,
    `build-and-deploy` 실패.
  - 직전 image 부재: `Rollback=UNAVAILABLE`, 수동 복구 필요, `build-and-deploy` 실패.
  - SSH/action 실패나 구조화 결과 부재: `Rollback=UNKNOWN`, 수동 복구 필요, `build-and-deploy` 실패.
- summary에는 고정된 판정, commit SHA와 image name만 기록하고 캡처한 원격 stdout 원문은 쓰지 않는다.
  임의의 민감 문자열을 출력에 넣은 dry run에서도 summary와 helper stdout에 그 문자열이 노출되지
  않는 계약을 확인했다.
- 성공·실패 이메일 action 두 개 모두 `continue-on-error: true`로 설정했다. 메일 전송 실패는
  `notify`를 실패시키지 않으며, 성공/실패 메일 선택은 계속 `needs.build-and-deploy.result`를 기준으로
  한다. 따라서 이메일 결과가 verify·build·deploy 판정을 덮어쓰지 않는다.
- focused 6개와 PR/Main/rollback 관련 계약 21개를 통과했다. `bash -n`, 모든 Workflow shell block의
  표현식 치환 후 Bash 문법, Ruby/Psych YAML·trigger·concurrency·dependency/result graph,
  credential 출력·광범위 삭제·migration·외부 DB/Redis marker 검색도 통과했다. `actionlint`와
  `shellcheck`는 로컬에 없어 실행하지 못했다.
- 첫 Spotless 사전 검사는 새 계약 테스트 한 줄의 포맷 차이로 실패했다. 그 줄만 고친 뒤 최종
  `./gradlew clean test spotlessCheck --no-daemon`을 새로 실행해 95개 테스트, 실패·오류·건너뜀 0개와
  Spotless 검사를 통과했다.
- 이 청크의 변경 범위는 `.github/workflows/cicd.yml`, 새
  `scripts/ci/report-deployment-result.sh`, 새 `DeploymentResultNotificationContractTest`, SSH action
  version 기대값을 갱신한 `MainCiCdWorkflowContractTest`, 이 문서다. `.github/workflows/build-test.yml`,
  production Java, rollback helper, private 설정과 Apple 키, 원격 `deploy-prod.sh`는 변경하지 않았다.
- 실제 GitHub runner에서 `ssh-action@v1.2.5` stdout과 실패 outcome이 의도대로 전달되는지, 실제 SMTP
  실패가 non-blocking으로 표시되는지, 실제 원격 배포 결과가 step summary에 나타나는지는 아직
  확인하지 않았다. 실제 SSH, Docker, health endpoint, SMTP, 운영 서버, GitHub 설정에 접근하거나
  commit·push·배포를 수행하지 않았다.

### Chunk 8. Final local acceptance verification

Status: `CONFIRMED`

목표는 외부 변경 전에 저장소의 최종 상태가 이 문서의 acceptance criteria를 만족하는지 확인하는
것이다.

- [x] 마지막 수정 이후 필수 Gradle 명령을 새로 실행한다.
- [x] 두 Workflow의 문법, trigger, 권한, job 의존성, concurrency를 검증한다.
- [x] Workflow와 diff에 실제 credential 값, 외부 DB/Redis 접근, migration 명령이 없는지 검사한다.
- [x] 운영 image name, container name, port, profile, deploy script, health endpoint가 계약과 일치하는지
  확인한다.
- [x] 전체 diff와 변경 파일을 검토해 승인 범위 밖 수정이 없는지 확인한다.
- [x] acceptance criteria별 pass, fail, 미검증 근거를 표로 정리한다.

필수 검증 명령:

```bash
./gradlew clean test spotlessCheck --no-daemon
```

컨펌 게이트: 최종 로컬 검증 결과를 보고한 뒤 commit, push, PR 관련 별도 승인을 기다린다.

최종 로컬 acceptance 검증 결과 — 2026-07-18:

- `./gradlew clean test spotlessCheck --no-daemon`을 최종 상태에서 새로 실행해 `BUILD SUCCESSFUL`,
  95개 테스트, 실패·오류·건너뜀 0개와 Spotless 성공을 확인했다. 격리 context, v1 compatibility,
  PR/Main Workflow, 네 rollback dry run, 배포 summary·비차단 알림 계약 suite가 모두 일반 `test` task에
  포함됐다.
- Ruby/Psych로 두 Workflow YAML을 파싱하고 trigger, PR `contents: read`, job graph, main revision guard,
  concurrency와 정확한 Gradle 명령을 확인했다. 모든 Workflow `run`·원격 `script` block의 GitHub
  표현식을 안전한 값으로 치환한 뒤 `bash -n`을 통과했고 두 CI helper도 `bash -n`을 통과했다.
  `actionlint`와 `shellcheck`는 로컬에 없어 GitHub Actions 전용 schema와 shell 정적 분석은 실행하지
  못했다.
- PR Workflow는 `pull_request(main)`과 기존 `workflow_dispatch`만 사용하며 권한을
  `contents: read`로 명시한다. Main Workflow는 `push(main)`과 `workflow_dispatch`만 사용하고
  `verify -> build-and-deploy -> notify`, `poppang-production-deployment`,
  `cancel-in-progress: false`, `queue: max`를 유지한다. Main Workflow도 `contents: read`만 명시하고,
  PAT는 private 설정 다운로드 step에만, SSH host·user·key는 각 scp/ssh action 입력에만 전달한다.
- 실행 가능한 변경 범위에서 실제 token/key signature, literal Bearer token, credential 출력,
  외부 DB·Redis URL, migration·DDL/DML, 광범위한 `rm`·Docker prune를 검색해 발견하지 못했다. 허용된
  삭제는 고유 rollback tar 한 파일의 `rm -f --`뿐이다. 테스트 결과 로그에도 외부 DB·Redis·prod
  profile 연결 marker가 없었다.
- 운영 image와 container는 `poppang-prod`, tag는 `${GITHUB_SHA::7}`, deploy script는
  `/home/poppang/opt/deploy/deploy-prod.sh`, health endpoint는
  `http://localhost:4002/actuator/health`로 일치한다. Dockerfile은 container port `8080`을 노출하고
  health URL은 운영 서버의 localhost port `4002`를 사용한다. 실제 `4002 -> 8080` mapping과 active
  profile `prod`는 저장소 밖 원격 `deploy-prod.sh`가 결정하므로 이번 로컬 검증에서는 미검증이다.
- tracked 7개와 untracked 10개, 총 17개 변경 파일을 모두 검토하고 승인된 Chunk 2~7 변경과 정확히
  대조했다. production Java 변경은 `PopupWebController.java` 끝의 빈 줄 한 개 삭제뿐이며 동작 변경은
  없다. unexpected 변경, 삭제 파일, staged 파일, diff whitespace 오류는 없었다. 로컬 private 설정
  두 개와 Apple 키는 기존 위치에 존재하고 `.gitignore` 적용을 확인했으며 내용은 읽지 않았다.

Acceptance criteria 판정:

| Acceptance criterion | 판정 | 근거 |
|---|---|---|
| PR CI가 private 운영 설정과 외부 DB·Redis 없이 실행 | PASS | private download·secret·DB/Redis marker 부재, test profile·config location과 infrastructure bean 부재 계약 통과 |
| 필수 Gradle 명령 실패 시 PR CI 실패 | PASS | PR step이 정확한 명령을 `continue-on-error` 없이 직접 실행 |
| Main 배포 Workflow에 동일한 verify gate 존재 | PASS | 독립 `verify` job의 명령·동일 SHA checkout 계약 통과 |
| verify 실패·취소 시 image 생성·원격 배포 차단 | PASS | 모든 secret·production 작업이 `needs: verify`와 success 조건 뒤에만 존재 |
| `poppang-prod:<short-sha>` image와 `poppang-prod` container | PASS | Workflow env, version 명령, Docker tag와 remote helper 계약 통과; 실제 원격 실행은 미검증 |
| production 배포 동시 실행 방지 | PASS | 고정 concurrency group, `cancel-in-progress: false`, `queue: max` 정적 계약 통과; 실제 runner queue는 미검증 |
| 60초 내 unhealthy 시 직전 image 자동 재배포 | PASS | 60초/12회 health 계약과 rollback dry run 통과; 실제 endpoint·원격 Docker는 미검증 |
| rollback 성공 여부와 무관하게 실패한 신규 배포는 Workflow 실패 | PASS | rollback 성공·실패 dry run 모두 exit 1, summary gate 계약 통과 |
| 이메일 실패가 build·test·deploy 결과를 변경하지 않음 | PASS | 두 email step의 `continue-on-error: true` 계약 통과; 실제 SMTP 실패 실행은 미검증 |
| Workflow에 DB migration·외부 DB/Redis 접근 없음 | PASS | 실행 경로 정적 검색과 test runtime isolation 계약 통과 |
| 기존 v1 compatibility 계약이 CI에서 통과 | PASS | 고정 78 endpoint inventory와 익명 Security filter 계약 2개 통과 |

- FAIL 항목은 없다. 미검증 항목은 실제 GitHub runner/action schema·queue·effective token 권한,
  private 파일 실제 다운로드, Docker image build·전송, SSH/scp, 원격 command·Docker·health endpoint,
  저장소 밖 `deploy-prod.sh`의 port/profile/volume 동작, SMTP, branch protection과 실제 status check다.
  이들은 Chunk 8의 로컬·비배포 범위에서 실행하지 않았다.
- `DEPLOYMENT.md`는 변경 범위 밖이라 수정하지 않았으며 일부 pre-refactor Main Workflow 설명이 남아
  있다. 현재 승인 설계와 구현 상태의 기준은 이 문서이며, 운영 runbook 동기화는 별도 승인 작업으로
  남는다.
- 실제 credential, private 파일 내용, 운영 서버와 GitHub 설정을 조회·변경하지 않았고 commit, push,
  PR, image 생성·전송, 실제 배포를 수행하지 않았다.

### Chunk 9. GitHub rollout and branch protection

Status: `IN_PROGRESS`

목표는 검증된 변경만 GitHub에 올리고 `main`의 정상 진입 경로를 PR로 제한하는 것이다.

- [x] commit 대상 파일과 예정 메시지를 제시하고 명시적 commit 승인을 받는다.
- [ ] 승인된 commit을 만든 뒤 별도의 push 승인을 받는다.
- [ ] feature branch를 push하고 `main` 대상 PR에서 PR CI가 실제로 성공하는지 확인한다.
- [ ] `main`에 PR 필수, PR CI required status check, 관리자 긴급 우회 규칙을 적용한다.
- [ ] branch protection 적용 결과와 우회 권한을 읽기 전용으로 재확인한다.
- [ ] PR 병합이 production 배포를 시작한다는 점을 알리고 병합 전에 별도 컨펌을 받는다.

검증과 완료 조건:

- 일반 사용자의 `main` 직접 push가 차단되고 PR CI 없이는 병합할 수 없어야 한다.
- 관리자 우회 push도 Main Workflow의 `verify`를 건너뛸 수 없어야 한다.
- 실제 병합·production 배포 결과는 GitHub Actions에서 별도로 확인해야 한다.

컨펌 게이트: GitHub 설정과 PR CI 결과를 보고하고, production 배포를 유발하는 병합 승인을 별도로
기다린다.

GitHub rollout 진행 메모 — 2026-07-18:

- 승인된 변경 17개와 `ci: CI/CD 안전 검증과 배포 보호 강화` 메시지로 로컬 commit을 만드는 명시적
  승인을 받았다. 이 승인은 push, PR, GitHub 설정 변경 또는 병합 승인으로 간주하지 않는다.
- 사용자는 일반 Terminal의 GitHub keyring 인증이 정상이라고 확인했다. Codex 실행 환경에서는
  `GH_TOKEN`과 `GITHUB_TOKEN`을 제거한 뒤에도 `gh auth status -h github.com`이 invalid token으로
  실패하므로 Codex의 keyring 접근은 별도 미검증 항목으로 남긴다. 이는 로컬 commit을 막지 않지만,
  push와 PR은 별도 승인 및 사용 가능한 인증 환경을 확인하기 전까지 수행하지 않는다.
- PR #5의 Email Notifications 실행 `29646436753`은 기존 `email-notify.yml`이 CodeRabbit 리뷰 본문을
  Bash `run` 블록에 직접 삽입해 백틱·코드 블록을 명령으로 해석하면서 exit code 127로 실패했고 이메일
  단계가 건너뛰어진 blocker를 확인했다. 이 Workflow는 기존 파일로 commit `2d6d76c`의 변경 대상은
  아니었다.
- focused 보안 계약 테스트로 command substitution의 sentinel 생성을 RED에서 재현한 뒤, 모든 GitHub
  이벤트 값을 step-level `env`와 인용된 Bash 변수로 전달하고 충돌 방지 multiline delimiter 및
  `contents: read`를 적용해 GREEN을 확인했다. 실제 GitHub runner 재확인은 새 commit·push 승인 이후의
  미검증 항목이며 Chunk 9 상태는 `IN_PROGRESS`로 유지한다.

## CI contract

### PR workflow

- `main` 대상 `pull_request`에서 실행한다.
- 최소 권한은 `contents: read`로 제한한다.
- private repository의 운영 설정과 운영 credential을 다운로드하지 않는다.
- 명시적인 `test` profile 또는 격리된 test application을 사용한다.
- DataSource·JPA·Redis auto-configuration이 필요하지 않은 테스트에서는 이를 명시적으로 끈다.
- 실제 Redis 검증이 이후 필요해지면 GitHub runner 내부의 폐기 가능한 Redis만 사용한다. 외부
  Redis 주소는 허용하지 않는다.
- 실행 명령은 다음으로 고정한다.

```bash
./gradlew clean test spotlessCheck --no-daemon
```

- 기존 v1 endpoint inventory와 익명 접근 호환성 테스트를 필수 테스트 집합에 포함한다.
- JWT v2는 청크별 focused test를 같은 `test` task에서 실행되도록 추가한다.
- API 약 100개에 대한 전체 E2E 테스트를 CI 필수 조건으로 만들지는 않는다.

### Main verification

- 운영 배포 Workflow 안에 별도 `verify` job을 둔다.
- Workflow 권한은 `contents: read`로 제한한다.
- `verify`는 private 운영 설정을 다운로드하기 전에 PR CI와 같은 명령을 실행한다.
- 이미지 생성·전송·배포 job은 `needs: verify`로 연결한다.
- `verify` 실패 또는 취소 시 운영 설정 다운로드, 이미지 생성, 원격 배포를 실행하지 않는다.
- `PERSONAL_ACCESS_TOKEN`은 private 설정 다운로드 step에만 전달하고, 서버 host·user·SSH key는 이를
  사용하는 각 scp/ssh action 입력에만 전달한다.

## Branch protection

`main`에는 다음 최소 규칙을 적용한다.

- PR을 통해서만 일반 변경을 병합한다.
- PR CI 성공을 required status check로 지정한다.
- 관리자는 운영 긴급 상황에서만 우회할 수 있다.

관리자 우회는 일상적인 배포 경로가 아니며, 우회 배포도 Main Workflow의 `verify`를 통과해야 한다.

## Build and deployment contract

- 운영 대상은 현재 하나뿐이며 GitHub Actions CD의 대상은 production이다.
- 이름과 포트는 다음으로 통일한다.

| 항목 | 값 |
|---|---|
| Docker image repository | `poppang-prod` |
| 운영 container | `poppang-prod` |
| 외부 port | `4002` |
| container port | `8080` |
| active profile | `prod` |
| deploy script | `/home/poppang/opt/deploy/deploy-prod.sh` |
| health endpoint | `http://localhost:4002/actuator/health` |

- 이미지 tag는 기존처럼 Git commit short SHA를 사용한다.
- CD에서만 private repository의 운영 설정과 Apple 로그인 키를 다운로드한다.
- 다운로드는 HTTP 오류, 빈 파일, HTML·`Not Found` 응답을 실패로 처리한다.
- 현재 운영 설정을 JAR에 포함하는 방식과 원격 스크립트의 평문 secret은 이번 범위에서 유지한다.
- 배포는 동일 commit에서 검증을 통과한 뒤 생성한 JAR와 이미지로 수행한다.
- `workflow_dispatch`는 `main` revision만 운영에 배포할 수 있도록 제한한다.

## Deployment concurrency

- production 배포는 한 번에 하나만 실행한다.
- 새 배포가 시작돼도 진행 중인 production 배포를 취소하지 않는다.
- 대기 중인 후속 실행은 앞선 실행이 끝난 뒤 시작한다.

## Health check and rollback

완전한 무중단 배포는 목표가 아니다. 현재 스크립트가 기존 컨테이너를 먼저 제거하므로 정상
배포에서도 짧은 중단이 발생할 수 있다.

운영 배포 직전에 다음 정보를 원격 서버에서 기록한다.

- 현재 실행 중인 `poppang-prod` 컨테이너의 image name과 tag
- 해당 image가 원격 Docker daemon에 존재하는지 여부
- 자동 롤백에 사용할 임시 tar 경로

기존 이미지가 있으면 신규 배포 전에 롤백용 tar를 생성한다. 그 다음 기존 `deploy-prod.sh`를 수정하지
않고 신규 tar와 image name을 전달한다.

신규 배포 후 health endpoint가 HTTP 성공이고 응답 상태가 `UP`인지 최대 60초 동안 재시도한다.

- 성공하면 배포를 성공 처리한다.
- 실패하면 기존 `deploy-prod.sh`에 롤백 tar와 직전 image name을 전달해 자동 재배포한다.
- 롤백 후 health check를 다시 실행한다.
- 롤백이 성공해도 신규 배포는 실패한 것이므로 Workflow 결과는 실패로 남긴다.
- 롤백마저 실패하면 이를 명확히 표시하고 즉시 수동 복구 대상으로 보고한다.
- 최초 배포처럼 직전 이미지가 없는 경우 자동 롤백할 수 없으므로 배포를 중단하거나 명시적인 수동
  복구 대상으로 처리한다.

로그에는 image name, commit SHA, health check 결과만 남기고 환경변수와 credential은 출력하지
않는다.

## Notification contract

- GitHub Actions job 결과를 배포 성공 여부의 기준으로 삼는다.
- 이메일은 보조 알림이다.
- 이메일 인증·전송 실패가 성공한 배포를 실패로 바꾸거나 실제 배포 실패 원인을 가리지 않도록
  알림 step을 non-blocking으로 처리한다.
- 배포 실패와 롤백 실패는 GitHub Actions summary에 항상 남긴다.

## Database and external service safety

- CI와 Main `verify`는 운영·개발 외부 DB/Redis에 연결하지 않는다.
- Workflow는 DDL, `UPDATE`, `INSERT`, `DELETE`, migration SQL을 실행하지 않는다.
- DB migration은 대상 DB, SQL, 영향, 롤백 방법을 별도로 검토하고 명시적으로 승인한 뒤 수동으로
  수행한다.
- JWT refresh/signup token의 Redis 통합 테스트가 필요해지면 CI runner 내부의 폐기 가능한 Redis를
  사용하고 production endpoint를 설정할 수 없게 한다.

## JWT v2 compatibility and rollout

- 20개 청크를 한 PR에 넣지 않는다.
- 정확한 production 단위는 JWT 구현 체크리스트의
  [deployment wave map](../superpowers/plans/2026-07-15-jwt-v2-migration.md#merge-and-production-deployment-wave-map)을
  따른다. 청크 하나 또는 provider·read/write 경계로 더 나눈 작은 단위만 PR로 만든다.
- `main` 병합은 자동 production 배포를 시작하므로 push 승인과 별도로 각 wave의 merge·배포
  승인을 받는다. 직전 wave의 smoke와 관찰이 끝나기 전에는 다음 wave를 병합하지 않는다.
- 진행 보고마다 `현재 구현 청크 x/20`, `현재 배포 wave n/7`, `운영 배포 완료 m/7`과
  `지금 배포하면 안 됩니다` 또는 `지금 배포할 단계입니다 — 별도 승인 필요` 판단을 명시한다.
- 앱 클라이언트가 해당 v2 endpoint로 전환하기 전까지 신규 v2 API는 사용되지 않는다는 전제로
  점진적으로 배포한다.
- Entity/JPA/schema 영향이 예상되면 코드 수정 전에 DDL·호환성·lock·backup·rollback을 보고하고
  별도 승인을 받는다. schema 영향이 없더라도 Entity 파일 변경은 `DDL: N/A` 근거와 함께 승인받는다.
- 기존 v1 controller, path, method, parameter, body, response, status, media type과 익명 접근 동작을
  변경하지 않는다.
- v1 제거는 iOS와 Android의 endpoint별 v2 전환이 확인된 뒤 별도 작업으로 수행한다.

2026-08-03 현재 JWT 구현 청크 0~13(14/20)과 운영 배포 Wave 1~4(4/7)가 완료됐다. Wave 5는
청크 11~13의 구현·로컬 검증을 마쳤지만 아직 운영 배포 전이다. 최근 Wave 4의
PR #9 merge 뒤 production run `30627748110`이 성공했고 Actuator UP, 대표 v1 익명 HTTP 200,
v2 인증 실패 HTTP 401 smoke를 확인했다. 유효 Access Token 정상 요청 smoke는 클라이언트 전환
전 필수 미검증 항목으로 남아 있다.

## Accepted risks and deferred work

- 단일 컨테이너 교체 방식이므로 완전한 무중단을 보장하지 않는다.
- 원격 `deploy-prod.sh`의 credential 평문 관리 방식은 유지한다.
- 노출 가능성이 있는 credential의 교체와 서버 env 파일 또는 secret manager 이전은 후속 작업이다.
- 운영 설정과 Apple 로그인 키가 build artifact에 포함될 수 있는 기존 구조를 유지한다.
- 자동 롤백은 애플리케이션 image만 되돌리며 DB schema나 데이터 변경을 되돌리지 않는다. 이 때문에
  DB migration을 CD에서 자동 실행하지 않는다.

## Acceptance criteria

- PR CI는 private 운영 설정 없이 실행되며 외부 DB/Redis에 연결하지 않는다.
- `./gradlew clean test spotlessCheck --no-daemon` 실패 시 PR CI가 실패한다.
- `main` 배포 Workflow에도 동일한 `verify` gate가 존재한다.
- 검증 실패 시 Docker 이미지 생성과 원격 배포가 실행되지 않는다.
- 운영 이미지는 `poppang-prod:<short-sha>`로 생성되고 `poppang-prod` 컨테이너로 실행된다.
- production 배포가 동시에 두 개 실행되지 않는다.
- 신규 배포가 60초 안에 healthy 상태가 되지 않으면 직전 이미지가 자동 재배포된다.
- 자동 롤백 성공 여부와 무관하게 실패한 신규 배포는 Workflow 실패로 표시된다.
- 이메일 발송 실패는 실제 build·test·deploy 결과를 변경하지 않는다.
- Workflow에서 DB migration 또는 외부 DB/Redis 접근이 발생하지 않는다.
- 기존 v1 호환성 계약 테스트가 CI에서 계속 통과한다.

## Open questions

설계 결정은 모두 확정됐다. 구현 시에는 원격 서버의 Docker image 저장 공간, `curl` 사용 가능 여부,
GitHub branch protection 적용 권한을 검증해야 한다. 이는 계약 변경이 아니라 구현 전 환경 확인
항목이다.
