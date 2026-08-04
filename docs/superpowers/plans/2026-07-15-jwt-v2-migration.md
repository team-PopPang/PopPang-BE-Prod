# JWT v2 Migration Implementation Plan

**Goal:** 공개 상태인 v1 API를 그대로 유지하면서 전체 v2 API에 JWT 기반 인증·인가를 도입하고,
모바일·관리자·worker가 안전하게 전환된 뒤 v1을 별도 변경으로 제거할 수 있게 한다.

**Source of truth:**

- `docs/superpowers/specs/2026-07-15-jwt-v2-migration-design.md`
- `docs/superpowers/specs/2026-07-15-signup-token-flow-design.md`
- 저장소 루트 `AGENTS.md`

**Current snapshot (2026-08-04):** 구현 청크 0~16, 총 17/20개와 운영 배포 Wave 1~5,
총 5/7개가 완료됐다. Wave 5의 청크 11~13은 PR #11과 production run `30788767383`으로
운영 반영됐고 health·대표 v1 익명 접근·v2 무토큰 차단 smoke를 통과했다. Wave 6의 청크
14~16은 개인화 popup·제보·공개 Web API 20개를 모두 구현하고 로컬 회귀를 마쳤지만 아직
commit·PR·운영 배포 전이다. 다음 단계는 Wave 6 release 검수이며, iOS/AOS/ETL traffic은 아직
v2로 전환하지 않았다. 따라서 대응 v1 API와 익명 호출 계약은 계속 유지한다.

**Architecture:** v1과 v2 Controller/DTO/application/service/provider verifier를 분리한다. v1은
승인된 실험용 token endpoint 두 개를 제외하고 현재 운영 구현을 동결한다. V2를 위한 공통화나
리팩터링을 이유로 v1 provider 호출, 조회 조건, service orchestration을 수정하지 않으며 필요한
중복은 전환 기간에 수용한다. v2 앱 API는 `TOKEN_ACCESS`, 회원가입은 `TOKEN_SIGNUP`, 관리자는
`TOKEN_ACCESS + ROLE_ADMIN`, worker는 `SERVICE_WORKER`를 요구한다. JWT의 `sub`를 caller
identity로 사용하며 Refresh/Signup Token의 최신 상태와 원자적 소비는 Redis가 담당한다.

## Global constraints

- `POST /api/v1/auth/token/test`, `POST /api/v1/auth/refresh`만 삭제한다.
- 그 밖의 v1 method, path, parameter, status, media type, response schema와 익명 호출은 유지한다.
- 현재 운영 중인 v1 Controller, DTO, application/service 구현과 provider HTTP 연동 구현은
  수정하지 않는다. V2 코드가 v1 구현 클래스나 V2용 verifier를 주입해 실행 경로를 바꾸지 않는다.
- v2 Controller에 v1 mapping을 같이 붙이지 않고 버전별 presentation adapter를 분리한다.
- v2용 Controller, DTO, application/service, verifier를 새 클래스로 만들고 v1 Service에 새
  V2 entry point를 추가하지 않는다. 임시 코드 중복은 허용하며 v1 삭제 전에는 공통화하지 않는다.
- SecurityConfig·ErrorCode·Entity·Repository 같은 공유 기반은 v2 연결에 필요한 최소 additive
  변경만 허용한다. 기존 v1 메서드·query·저장 의미를 교체하지 않으며 Entity/JPA 영향은 별도
  승인 gate를 적용한다.
- v2 request의 caller `userUuid`는 제거하고 검증된 principal을 사용한다. popupUuid,
  recommendId, submissionId, keyword, worker target userUuid는 유지한다.
- v2 앱 API는 별도 선언을 빠뜨려도 기본 `TOKEN_ACCESS`가 적용되어야 한다.
- private config, JWT secret, API Key, token 원문을 저장소·테스트 fixture·로그에 기록하지 않는다.
- 서버 변경은 아래 wave로 점진 배포하되, 필요한 v2 endpoint와 회귀·보안 테스트가 모두 준비되기
  전에는 iOS/AOS/ETL의 실제 traffic을 v2로 전환하지 않는다.
- `main` merge는 현재 GitHub Actions에 의해 운영 배포를 시작하므로, 전체 JWT 변경을 한 PR로
  push·merge하지 않는다. 아래 배포 wave마다 별도 branch/PR/merge와 운영 확인을 수행한다.
- Entity/JPA mapping 또는 DB schema 영향 가능성이 보이면 해당 코드 수정 전에 작업을 멈추고
  사용자에게 영향과 DDL 초안을 먼저 보고한다. 명시적 승인을 받기 전에는 Entity 코드도 수정하지
  않는다.
- 각 작업 묶음이 통과해도 자동으로 commit하지 않는다. commit 직전에 대상 파일과 예정 메시지를
  사용자에게 알리고 매번 새 승인을 받는다. push와 배포도 별도 승인 없이는 실행하지 않는다.
- iOS/AOS/ETL이 모두 v2로 전환되고 v1 실호출 0건을 확인해 v1을 삭제한 뒤에만 V2 코드 품질,
  중복 제거와 아키텍처 리팩터링을 별도 승인·배포 작업으로 수행한다.
- 각 청크 완료 시 설계 문서의 `v1 → v2 동작 변경 기록`과 마이그레이션 매트릭스에 v1 유지
  내용, v2 변경, 클라이언트 변경, DB/Entity 영향, 구현·검증 상태를 즉시 갱신한다. 계획만 확정된
  항목과 실제 구현·테스트가 끝난 항목을 구분한다.

## Execution chunk map

72개 v2 mapping을 한 번에 구현하지 않는다. 아래 청크는 구현·검토·commit 승인 단위다. 실제
운영 반영은 뒤의 7개 deployment wave가 연관된 구현 청크를 production-safe release 단위로 묶는다.
일부 v2가 먼저 배포되어도 모바일·worker traffic은 필요한 서버 wave가 모두 안정화되기 전까지
전환하지 않는다.

| 청크 | 범위 | v2 API 수 | 완료 | 완료 기준 |
|---:|---|---:|---|---|
| 0 | v1 endpoint inventory와 익명 호출 기준선 | 0 | ✅ 완료 (2026-07-16) | 삭제 예외 2개 외 v1 계약 자동 비교 |
| 1 | Users audit/expand SQL, SignupStatus, identity repository | 0 | ✅ 완료 (2026-07-20) | 기존 binary 호환 DB 확장 검증 |
| 2 | Access/Refresh/Signup JWT claim·검증·오류 | 0 | ✅ 완료 (2026-07-28) | 고정 Clock JWT 단위 테스트 |
| 3 | Refresh/Signup Redis hash·TTL·원자 연산 | 0 | ✅ 완료 (2026-07-28) | 실제 Redis 동시성 테스트 |
| 4 | v1/v2/internal SecurityFilterChain과 두 인증 필터 | 0 | ✅ 완료 (2026-07-29) | 경로·authority별 401/403 계약 |
| 5 | Refresh와 logout | 2 | ✅ 완료 (2026-07-31) | strict rotation·idempotent logout |
| 6 | V2 provider 검증과 Kakao mobile login/signup | 2 | ✅ 완료 (2026-07-31) | v1 Kakao 구현 격리 + v2 수직 흐름 |
| 7 | Google·Apple mobile login/signup adapter | 4 | ✅ 완료 (2026-07-31) | 두 provider v1 회귀 + v2 흐름 |
| 8 | 사용자 조회·알림 동의·닉네임·탈퇴·FCM | 6 | ✅ 완료 (2026-07-31) | JWT 본인만 변경 가능 |
| 9 | 찜 4개 + 알림 키워드 3개 | 7 | ✅ 완료 (2026-07-31) | request의 caller UUID 제거 |
| 10 | 알림함 조회·삭제·읽음 | 3 | ✅ 완료 (2026-07-31) | principal 사용자 알림만 처리 |
| 11 | 일반 popup 핵심 조회 | 7 | ✅ 완료 (2026-08-03) | 전체·상세·검색·예정·진행·지역·랜덤 회귀 |
| 12 | 일반 popup 필터·추천 조회 | 6 | ✅ 완료 (2026-08-03) | filter·related·recommend target 유지 |
| 13 | 조회수 3개 + app recommend master 2개 | 5 | ✅ 완료 (2026-08-03) | 기존 count/featured 계약 유지 |
| 14 | 개인화 popup 핵심 조회 + scroll 보완 | 7 | ✅ 완료 (2026-08-03) | JWT 기반 찜 여부·사용자 결과·cursor scroll |
| 15 | 개인화 popup 고급 조회 5개 + 제보 1개 | 6 | ✅ 완료 (2026-08-03) | principal 제보자와 개인화 필터 |
| 16 | 공개 Web popup 6개 + Web recommend 1개 | 7 | ✅ 완료 (2026-08-04) | GET/HEAD permitAll 전용 namespace |
| 17 | Admin popup·제보 API | 5 | ⬜ 미완료 | 모든 mapping에 현재 ROLE_ADMIN 강제 |
| 18 | crawler·notification worker internal API | 5 | ⬜ 미완료 | API Key와 target UUID 분리 |
| 19 | OpenAPI·관측·migration matrix·전체 회귀 | 0 | ⬜ 미완료 | spotlessCheck, clean test/build |
|  | **합계** | **72** | **17/20 완료** |  |

## Merge and production deployment wave map

현재 `.github/workflows/cicd.yml`은 `main` push를 운영 배포로 연결한다. 따라서 아래 각 wave는 단순
개발 묶음이 아니라 **별도 PR, 별도 merge 승인, 별도 운영 배포와 smoke/rollback 판단 단위**다.
앞 wave가 운영에서 안정화되기 전에는 다음 wave를 `main`에 merge하지 않는다. 여러 wave를 한
PR로 합치려면 사용자에게 범위와 위험을 다시 보고하고 별도 승인을 받는다.

| 배포 wave | 구현 청크 | 범위 | v2 API 수 | 완료 | merge 전 필수 gate |
|---:|---:|---|---:|---|---|
| 1/7 | 0~1 | v1 호환 기준선·SignupStatus·Users identity 기반 | 0 | ✅ 완료 (2026-07-20) | 계약 테스트, DB-E1 적용·검증, v1 signup 상태 전이 |
| 2/7 | 2~3 | JWT 계약·Redis 원자 token 저장소 | 0 | ✅ 완료 (2026-07-28) | private config, legacy JWT 비침해, 실제 Redis 테스트 |
| 3/7 | 4~8 | Security 경계·Refresh·logout·소셜 인증·사용자 self-service | 14 | ✅ 완료 (2026-07-31) | v1 회귀와 401/403, strict rotation, provider별 인증과 본인 한정 |
| 4/7 | 9~10 | 찜·알림 키워드·알림함 | 10 | ✅ 완료 (2026-07-31) | caller UUID 제거와 타 사용자 접근 거절 |
| 5/7 | 11~13 | 일반 popup 조회·필터·추천·조회수·recommend master | 18 | ✅ 완료 (2026-08-03) | v1 결과 회귀와 filter/count/featured 계약 |
| 6/7 | 14~16 | 개인화 popup·popup 제보·공개 Web API | 20 | 🟡 구현 완료·미배포 | principal 개인화·제보자 검증, Web GET/HEAD permitAll |
| 7/7 | 17~19 | Admin·worker·OpenAPI·관측·matrix·전체 안정화 | 10 | ⬜ 미완료 | ROLE_ADMIN·API Key, ETL 계획, clean test/build와 전체 회귀 |
|  |  | **합계** | **72** | **5/7 완료** |  |

DB-E1은 1/7의 선행 수동 DB gate이며 서버 배포 wave 숫자에는 포함하지 않는다. 대상
DB·backup·backfill DML·default DDL·lock·rollback을 별도로 승인받고 적용·검증한 뒤에만 관련
Users 코드를 `main`에 merge할 수 있다. 실제 이력에서는 구현 청크 0과 1을 각각 별도 배포했으며,
두 배포를 합쳐 완료된 1/7 기반 wave로 기록한다. 따라서 배포 wave 수는 release 진행 단위이고,
장애 대응이나 이미 완료된 선행 분할로 실제 production deploy 실행 횟수와 다를 수 있다.

1/7~7/7은 서버를 안전한 release 단위로 배포하기 위한 순서이며 모바일이나 worker를 곧바로 해당 wave로
전환한다는 뜻이 아니다. iOS/AOS/ETL 전환은 필요한 서버 wave가 모두 안정화된 뒤 별도 승인과 일정으로
진행한다. 새 v2 endpoint가 일부 배포되어도 기존 v1은 계속 동일하게 동작해야 한다.

모든 API wave는 해당 endpoint의 OpenAPI 보안 표기와 저카디널리티 관측을 함께 포함한다. 7/7은
이를 처음 추가하는 wave가 아니라 전체 누락과 migration matrix를 최종 검증하는 wave다.

모든 wave의 공통 진입 gate는 직전 배포 healthy·관찰 완료, focused test와 v1 contract test,
필수 설정 선반영, rollback image SHA 확인, 미승인 Entity/DDL 없음이다. merge 직전에는
`main merge가 운영 배포를 시작한다`는 사실과 대상 파일·smoke·rollback을 다시 보고하고 명시적
승인을 받는다.

공통 종료 gate는 Actions verify/deploy 성공, 배포 image SHA 기록, Actuator UP, 대표 v1 익명
smoke, 해당 v2의 token 없음·권한 부족·정상 요청 smoke와 새 5xx/DB·Redis 오류 부재다. 종료 gate를
확인하기 전에는 다음 wave를 merge하지 않는다.

### Commit, Push, PR, merge and deployment protocol

구현 청크와 운영 배포 wave는 같은 단위가 아니다. 구현 청크마다 독립적으로 검증하고 commit하되,
Push·PR·merge·운영 배포는 위 표의 wave 경계에서 수행한다. 한 wave에 여러 구현 청크가 있으면 같은
feature branch에 순서대로 commit하고, wave의 마지막 구현 청크까지 검증된 뒤 PR을 만든다.

GitHub에 쓰기 작업을 하기 전에는 `gh auth status -h github.com`과
`gh api user --jq .login`으로 활성 계정을 확인한다. 로그인 이름은 반드시 `dev-song42`여야 한다.
인증 확인이 실패하거나 다른 계정이면 commit 이후의 Push·PR·merge·배포 작업을 즉시 중단하고
사용자에게 보고한다. 에이전트가 임의로 계정을 전환하거나 다른 계정으로 계속 진행하지 않는다.

각 구현 청크는 다음 순서로 처리한다.

1. `git status`와 diff를 확인하고 기존 사용자 변경과 현재 청크의 범위를 구분한다.
2. Entity/JPA/schema 영향 가능성을 먼저 판정하고, 영향이 있으면 별도 Entity/DDL 승인을 받을 때까지
   구현을 시작하지 않는다.
3. 실패 테스트를 먼저 추가하고 최소 구현 뒤 focused test, v1 contract test, compile과 formatting을
   실행한다. 외부 DB·Redis 접속 가능성이 있으면 실행하지 않고 차단 항목으로 보고한다.
4. 검증 결과와 정확한 commit 대상 파일, 예정 commit message를 보고한다.
5. 사용자의 **현재 commit에 대한 명시적 승인**을 받은 뒤에만 해당 파일을 `git add`하고 commit한다.
   일반적인 진행 승인이나 이전 commit 승인을 다음 commit 승인으로 재사용하지 않는다.
6. commit 후 작업 트리와 commit SHA를 확인한다. Push는 commit 승인과 별개이므로 정확한 branch와
   commit을 보고하고 **별도 Push 승인**을 받은 뒤 실행한다.
7. Push 뒤 원격 branch가 같은 commit SHA를 가리키는지 확인한다. 같은 PR에 문서나 코드를 추가해야
   해도 새 변경은 다시 검증하고 새 commit 승인과 새 Push 승인을 각각 받는다.

PR과 운영 배포는 다음 규칙을 따른다.

1. PR은 배포 wave마다 하나를 만들며 base는 `main`, head는 해당 wave의 feature branch로 고정한다.
2. PR 생성도 외부 상태 변경이므로 사용자에게 제목, base/head, Draft 여부를 보고하고 승인받는다.
3. 기본값은 CI와 자동 리뷰가 모두 실행되는 **Ready for review**다. 미완성 공유가 목적이고 사용자가
   명시적으로 요청한 경우에만 Draft로 만들며, Draft에서는 자동 리뷰가 생략될 수 있음을 알린다.
4. PR 제목에는 `JWT v2 Wave n`과 범위를 표시한다. 본문에는 변경 목적·v1 호환성·Entity/DB 영향,
   private config gate, 실행한 테스트, 미검증 항목, 배포 smoke와 rollback 계획을 기록한다.
5. 필수 CI와 리뷰가 모두 통과해도 자동 merge하지 않는다. 결과와 최종 head SHA를 사용자에게
   보고하고, review 수정이 생기면 수정 commit과 Push에 각각 새 승인을 받는다.
6. `main` merge가 production 배포를 시작한다는 사실을 다시 알리고, PR·head SHA·CI 결과·설정/DB
   gate·smoke·rollback 준비를 보고한 뒤 **별도 merge·운영 배포 승인**을 받는다.
7. merge 뒤 GitHub Actions production deploy, 배포 image SHA, Actuator UP, 대표 v1 익명 smoke,
   해당 v2의 401/403·정상 요청 smoke와 DB·Redis 오류를 확인한다.
8. 종료 gate가 실패하면 다음 구현 wave를 시작하지 않고 신규 v2 traffic을 중단한 뒤 승인된 rollback
   절차를 따른다. 성공이 확인된 뒤에만 `운영 배포 완료 m/7`을 증가시킨다.

### Mandatory progress report

각 배포 wave를 시작할 때와 구현·검증이 끝날 때 아래 형식으로 사용자에게 보고한다.

~~~text
현재 구현 청크: x/20 — 범위
현재 배포 wave: n/7 — 범위
구현 상태: 시작 전 | 구현 중 | 구현 완료
검증 상태: 미실행 | 일부 통과 | 전체 통과
운영 배포 완료: m/7
배포 판단: 지금 배포하면 안 됩니다 | 지금 배포할 단계입니다 — 별도 승인 필요 | 배포 완료
차단 항목: 없음 또는 구체적인 gate
다음 작업: 한 문장
~~~

`구현 청크 완료 x/20`을 `운영 배포 완료 n/7`로 표현하지 않는다. `운영 배포 완료` 숫자는
`main` merge, Actions production deploy, health와 smoke 확인이 모두 끝났을 때만 증가시킨다.
배포할 단계가 되면 `main merge가 운영 배포를 시작한다`고 다시 알리고 commit, push, merge·배포
승인을 각각 받는다. gate가 남아 있으면 반드시 `지금 배포하면 안 됩니다`라고 명시한다.

## Pre-implementation gates

아래 값은 코드에 추측으로 고정하지 않는다. 해당 작업을 시작하기 전에 실제 환경에서 확인한다.

1. 운영 MySQL major version, `users`의 실제 index/constraint 이름, 테이블 크기와 DDL lock 영향
2. 운영 Redis major version과 command/script 지원 범위
3. hidden Web OAuth callback의 실제 사용 여부와 현재 state/PKCE 처리 주체
4. 외부 Python 알림 worker의 alert 생성 POST 호출 여부와 v2 전환 일정
5. 로그인·signup·refresh rate limit 임계값과 운영 경보 수신 위치

확인 결과가 설계 계약을 바꾸면 구현을 진행하지 말고 설계 문서를 먼저 갱신해 승인받는다.

Rate Limit gate 5는 2026-07-31 사용자 승인으로 해소했다. 기본값은 로그인 10회/분(IP),
signup 5회/분(검증된 Signup Token subject), refresh 10회/분(검증된 Refresh Token subject)이며,
별도 private config가 없으면 이 값을 사용한다. 운영 경보 연동은 이번 구현 범위에 포함하지
않고 429 응답과 Redis key 관찰을 우선 사용한다.

### Mandatory Entity and DDL approval gate

다음 중 하나라도 예상되면 구현을 멈추고 사용자 확인부터 받는다.

- `@Entity`, `@Embeddable`, `@MappedSuperclass` 파일의 생성·수정·삭제
- 영속 field, enum, relation, column/index/constraint annotation 변경
- table, column, index, constraint, default, nullability 또는 저장 값 의미 변경
- native query가 의존하는 DB schema 계약 변경

승인 요청에는 최소한 다음을 포함한다.

1. 대상 배포 wave와 Entity
2. 대상 DB 환경, schema, table과 영향 row 규모
3. 필요한 이유와 기존·신규 binary 호환 범위
4. 작성할 audit, expand, verify, contract, rollback SQL의 정확한 경로와 내용
5. 예상 DDL algorithm, metadata lock, 쓰기 차단·downtime과 backfill 영향
6. backup, 적용 후 검증, 실패 시 rollback 방법

실제 schema 변경이면 Entity 코드보다 먼저 `docs/database/jwt-v2/`에 실행 순서가 분리된 DDL을
작성한다. DDL은 반복 실행 가능 여부와 실행 주체를 명시하고, destructive contract SQL은
no-return gate까지 분리한다. Entity 파일만 바뀌고 schema 영향이 없다면 가짜 SQL을 만들지 않고
`DDL 없음`과 그 근거를 승인 요청에 명시한다.

사용자가 해당 Entity와 DDL 범위를 명시적으로 승인하기 전에는 Entity 코드, migration SQL 실행,
외부 DB 접속을 하지 않는다. 일반적인 `진행해줘`나 이전 wave의 승인은 Entity/DDL 승인으로
간주하지 않는다. DDL 문서 작성 승인과 실제 DB 실행 승인은 서로 별개다.

1/7의 구현 청크 1은 `Users.signup_status` mapping을 포함하므로 운영 DB에 DB-E1이 승인·적용·검증되기
전에는 auto-deploy되는 `main`에 merge할 수 없었다. 로컬 복원 DB 연습 완료만으로 운영 적용을
승인하지 않았으며, 운영 적용은 별도 승인과 수동 검증을 거쳐 완료했다. 당시 nullable column이 이미
존재해 DB-E1은 column 추가가 아니라 NULL row backfill `UPDATE`와 default `ALTER`로 수행됐다.

---

## Task 1: v1 계약 기준선과 삭제 예외 고정

**Files:**

- Create: `src/test/java/com/poppang/be/contract/V1EndpointInventoryContractTest.java`
- Create: `src/test/java/com/poppang/be/common/security/V1SecurityCompatibilityTest.java`
- Create: `src/test/resources/contracts/v1-endpoints.txt`
- Reference without modification: `src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java`

**Steps:**

- [x] production `@RestController`의 merged mapping annotation에서 모든 v1
      method/path/consumes/produces를 추출한다.
- [x] 현재 mapping을 `v1-endpoints.txt`에 고정하고 두 token endpoint는 승인된 삭제 대상으로
      별도 표시한다.
- [x] DB·Redis 없이 대표 v1 공개 API가 Authorization header 없이 Security에서 차단되지 않는지
      검증한다.
- [x] hidden endpoint는 OpenAPI만으로 누락될 수 있으므로 reflection inventory를 기준으로 삼는다.
- [x] 현재 전체 테스트를 실행하고 기존 실패가 없음을 확인한다.

**Verification:**

```bash
./gradlew test --tests com.poppang.be.contract.V1EndpointInventoryContractTest
./gradlew test --tests com.poppang.be.common.security.V1SecurityCompatibilityTest
./gradlew test
```

**Exit gate:** 삭제 승인된 두 endpoint 외 v1 계약을 자동 비교할 수 있어야 한다.

**Status:** 완료 (2026-07-16). v1 78개 중 KEEP 76개와 DELETE_APPROVED 2개를 고정했다.

---

## Task 2: Users 확장 DB 변경과 social identity 기반 준비

**Files:**

- Create: `docs/database/jwt-v2/01-users-audit.sql`
- Create: `docs/database/jwt-v2/02-users-expand.sql`
- Create: `docs/database/jwt-v2/03-users-contract.sql`
- Create: `docs/database/jwt-v2/ROLLBACK.md`
- Create: `src/main/java/com/poppang/be/domain/users/entity/SignupStatus.java`
- Modify: `src/main/java/com/poppang/be/domain/users/entity/Users.java`
- Modify: `src/main/java/com/poppang/be/domain/users/infrastructure/UsersRepository.java`
- Test: `src/test/java/com/poppang/be/domain/users/entity/UsersTest.java`
- Test: `src/test/java/com/poppang/be/domain/users/infrastructure/UsersRepositoryContractTest.java`
- Test: `src/test/java/com/poppang/be/domain/users/infrastructure/UsersDatabaseMigrationContractTest.java`
- Modify: `src/test/java/com/poppang/be/PoppangBeApplicationTests.java`
- Test: `src/test/java/com/poppang/be/TestContextSafetyContractTest.java`

**Steps:**

- [x] audit SQL에 uuid null·중복, provider/role null, nickname 기반 예상 SignupStatus, 삭제 상태 모순,
      provider+uid 중복을 조회하는 query를 작성한다.
- [x] MySQL 8.0.43 운영에서 nullable `signup_status`와 기존 `uq_users_uuid`를 확인하고 expand SQL을
      실제 enum/index 이름에 맞춘다.
- [x] 로컬 MySQL 9.2.0의 `poppang_restore_test_20260716`에서 expand 연습을 완료했다. 운영
      MySQL 8.0.43과의 버전 차이는 2026-07-19 사용자 결정으로 DB-E1의 동일-major 재연습
      차단 항목에서 제외했다. 이 예외는 이후 DB wave에 자동 적용하지 않는다.
- [ ] 구·신규 binary의 실제 DB 연결 호환성과 ALTER metadata lock 영향은 운영 적용·배포 판단에서
      별도로 확인하거나 명시적으로 위험을 수용한다.
- [x] 로컬 복원 DB에서는 기존 row를 audit 분류대로 `COMPLETED`/`PENDING`으로 backfill하고
      기본값을 PENDING으로 변경한 뒤 COMMIT했다. users 303건, NULL 0건과 상태별 건수를 확인했다.
- [x] 2026-07-20 사용자 제공 수동 증거로 운영 `poppang_prod_db`의 기존 row backfill과 default
      PENDING 변경을 완료했다. users 307건, NULL 0건, PENDING 122건, COMPLETED 185건,
      nickname 기준 불일치 0건과 nullable enum/default PENDING을 확인했다. 이상 row를 자동
      수정하지 않았고 `03-users-contract.sql`은 실행하지 않았다.
- [x] `Users`에 SignupStatus와 `startSignup`, `completeSignup`, `softDelete` 같은 의미 있는 상태 전이
      메서드를 추가하되 v1 DTO 기반 메서드는 v1 전환이 끝날 때까지 유지한다.
- [x] Kakao·Google·Apple v1 signup이 호출하는 `completeSignup(SignupRequestDto)`도
      `COMPLETED`를 기록한다. 2026-07-19 사용자에게 Entity 메서드 변경을 승인받아 regression
      test를 먼저 실패시킨 뒤 상태 전이 한 줄을 추가했다. `DDL: N/A — schema delta 없음`이다.
- [x] repository에 `findByProviderAndUid`, active 조회, uuid 쓰기 잠금 조회를 추가한다.
- [ ] contract SQL은 no-return 승인 뒤 SignupStatus NOT NULL과 provider+uid 복합 unique를 적용하고,
      그 전까지 기존 uid 단일 unique를 유지한다.
- [x] rollback 문서에 각 DDL 전 backup, 실행 주체, 예상 lock, 되돌릴 수 있는 마지막 지점을 적는다.

**Verification:**

```bash
./gradlew test --tests 'com.poppang.be.domain.users.*'
./gradlew compileJava
```

**Exit gate:** 로컬 복원 DB의 expand 결과와 v1 signup 상태 전이를 확인한다. DB-E1의 동일-major
재연습은 사용자 결정으로 생략하되, 실제 운영 데이터 수정과 DDL은 별도 승인을 받아 적용·검증한다.
구·신규 binary의 실제 DB 연결과 metadata lock을 확인하지 않으면 그 위험을 배포 판단에 명시한다.

**Status:** 청크 1의 DB/repository 기반과 테스트 안전 gate, 로컬 DB migration 연습, 운영 DB-E1
expand 적용은 완료됐다. 로컬 복원 DB에서는 users 303건, NULL 0건, 활성 COMPLETED 182건,
활성 PENDING 1건, 탈퇴 PENDING 120건과 nullable enum/default PENDING을 확인했다. 2026-07-20
운영 `poppang_prod_db`에서는 users 307건, NULL 0건, PENDING 122건, COMPLETED 185건, nickname
기준 불일치 0건과 nullable enum/default PENDING을 확인했다. 두 환경 모두
`03-users-contract.sql`은 실행하지 않았다. 운영 MySQL 8.0.43과 로컬 MySQL 9.2.0의 버전 차이는
사용자 결정으로 수용했고, 구현 청크 1의 v1 DTO signup 상태 전이 차단 항목도 해결됐다. 구·신규
binary의 실제 DB 연결 호환성과 운영 ALTER 당시 metadata lock 영향은 확인되지 않은 위험으로
남기며, production merge 전 코드 검증과 해당 위험의 수용 여부를 별도로 판단한다.

---

## Task 3: JWT 계약 구현

**Files:**

- Modify: `src/main/java/com/poppang/be/common/jwt/JwtProperties.java`
- Modify: `src/main/java/com/poppang/be/common/jwt/JwtTokenType.java`
- Modify: `src/main/java/com/poppang/be/common/jwt/JwtProvider.java`
- Create: `src/main/java/com/poppang/be/common/jwt/VerifiedJwt.java`
- Create: `src/main/java/com/poppang/be/common/jwt/JwtFingerprint.java`
- Create: `src/main/java/com/poppang/be/common/config/ClockConfig.java`
- Modify: `src/main/java/com/poppang/be/common/exception/ErrorCode.java`
- Test: `src/test/java/com/poppang/be/common/jwt/JwtProviderTest.java`
- Test: `src/test/java/com/poppang/be/common/jwt/JwtPropertiesTest.java`

**Steps:**

- [x] `SIGNUP` type과 `iss`, token별 `aud`, `sub`, `iat`, `exp`, `jti`, Access/Refresh 공통 `sid`를
      구현한다.
- [x] Access 15분, Refresh 30일 sliding, Signup 15분을 properties로 받는다.
- [x] parser가 signature/issuer/audience/algorithm/type을 한 번 검증하고 `VerifiedJwt`를 반환하게 한다.
- [x] `assertAccessToken`의 raw RuntimeException을 제거하고 모든 JWT 오류를 ErrorCode로 정규화한다.
- [x] HS256 secret 길이와 필수 property를 시작 시 검증하고 secret 자체는 로그에 남기지 않는다.
- [x] 고정 Clock으로 claim과 만료를 테스트하고 none/다른 algorithm, 잘못된 aud/typ를 각각 거절한다.

2026-07-21 기준 JWT focused test와 v1 호환 회귀 test는 통과했다. 운영 배포 전 private config의
신규 audience·Signup TTL 항목과 기존 secret의 256-bit 조건 충족 여부는 값 노출 없이 별도로
확인해야 한다.

**Verification:**

```bash
./gradlew test --tests com.poppang.be.common.jwt.JwtProviderTest
./gradlew test --tests com.poppang.be.common.jwt.JwtPropertiesTest
```

---

## Task 4: Redis 기반 Refresh/Signup 최신 token 저장소

**Files:**

- Modify: `build.gradle`
- Create: `src/main/java/com/poppang/be/domain/auth/redis/TokenHashRecord.java`
- Create: `src/main/java/com/poppang/be/domain/auth/redis/V2RefreshTokenRedisRepository.java`
- Create: `src/main/java/com/poppang/be/domain/auth/redis/V2SignupTokenRedisRepository.java`
- Create: `src/main/resources/redis/refresh-rotate.lua`
- Create: `src/main/resources/redis/refresh-compare-delete.lua`
- Create: `src/main/resources/redis/signup-compare-delete.lua`
- Test: `src/test/java/com/poppang/be/domain/auth/redis/V2TokenRedisRepositoryIntegrationTest.java`

**Steps:**

- [x] production과 같은 Redis version을 사용하는 Testcontainers test dependency를 추가한다.
- [x] `auth:v2:refresh:{userUuid}`와 `auth:v2:signup:{userUuid}`에 token 원문 대신 SHA-256 hash,
      jti, sid/issuedAt을 저장한다.
- [x] 저장과 TTL을 하나의 원자 명령으로 처리한다.
- [x] refresh compare-and-replace, logout compare-delete, signup compare-delete를 Lua로 원자화한다.
- [x] 같은 token 동시 rotation/consume에서 정확히 하나만 성공하는 실제 Redis 통합 테스트를 작성한다.
- [x] Redis timeout과 연결 실패를 mismatch와 구분해 `AUTH_STORE_UNAVAILABLE`로 변환한다.
- [x] timeout 뒤 자동 재시도를 하지 않고 TTL과 key prefix가 설계값과 일치하는지 검증한다.

2026-07-21 사용자 확인으로 운영 Redis가 `7.2.12`임을 식별했다. 운영 Redis에는 접속하지 않고
`POPPANG_TEST_REDIS_VERSION=7.2.12`로 동일 버전의 폐기 가능한 Testcontainers Redis를 실행해
TTL·Lua·refresh 동시 rotation·Signup 동시 consume·logout 통합 test 4개를 모두 통과했다.

**Verification:**

```bash
./gradlew test --tests com.poppang.be.domain.auth.redis.V2TokenRedisRepositoryIntegrationTest
```

**Exit gate:** Docker/Testcontainers를 사용할 수 없는 환경에서는 통합 테스트를 통과했다고 보고하지
않고 미검증 사유를 남긴다.

---

## Task 5: v1/v2/internal SecurityFilterChain 분리

**Files:**

- Modify: `src/main/java/com/poppang/be/common/security/SecurityConfig.java`
- Delete or replace: `src/main/java/com/poppang/be/common/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/poppang/be/common/security/V2JwtAuthenticationFilter.java`
- Create: `src/main/java/com/poppang/be/common/security/WorkerApiKeyAuthenticationFilter.java`
- Create: `src/main/java/com/poppang/be/common/security/JwtPrincipal.java`
- Create: `src/main/java/com/poppang/be/common/security/ApiAuthenticationEntryPoint.java`
- Create: `src/main/java/com/poppang/be/common/security/ApiAccessDeniedHandler.java`
- Create: `src/main/java/com/poppang/be/common/security/WorkerApiKeyProperties.java`
- Test: `src/test/java/com/poppang/be/common/security/SecurityChainContractTest.java`

**Steps:**

- [x] 1순위 `/api/v2/internal/**`, 2순위 `/api/v2/**`, 마지막 v1/기타 chain으로 분리한다.
- [x] internal은 `SERVICE_WORKER`, v2 admin은 `TOKEN_ACCESS + ROLE_ADMIN`, signup은 정확한 세 POST에
      `TOKEN_SIGNUP`, 나머지 v2는 기본 `TOKEN_ACCESS`를 요구한다.
- [x] permitAll은 정확한 login/refresh와 GET·HEAD `/api/v2/web/**`에만 적용한다.
- [x] v2 filter는 Access/Signup만 SecurityContext로 만들고 Refresh Bearer 사용을 거절한다.
- [x] Access 인증마다 DB의 active 상태와 현재 role을 조회한다. PENDING/deleted 사용자는 거절한다.
- [x] 401/403을 빈 body가 아닌 `ApiResponse` JSON으로 반환한다.
- [x] v1 chain에는 v2/worker filter를 적용하지 않고 header 없는 기존 익명 요청을 유지한다.
- [x] filter bean의 Servlet global auto-registration을 명시적으로 비활성화한다.
- [x] custom SpringDoc 경로 허용과 기본 Swagger 경로 차단을 기존대로 유지한다.

**Verification:**

```bash
./gradlew test --tests com.poppang.be.common.security.SecurityChainContractTest
./gradlew test --tests com.poppang.be.common.security.V1SecurityCompatibilityTest
```

**Status:** 완료 (2026-07-29). internal/v2/v1/인프라 SecurityFilterChain을 분리하고
Access·Signup·Refresh·Worker 용도와 현재 사용자 상태·role을 기준으로 401/403 `ApiResponse`
계약을 고정했다. focused test 47개와 전체 test 186개가 실패·오류·스킵 없이 통과했으며,
Entity/JPA/DB schema 변경과 외부 DB·Redis 접속은 없다. Wave 3 운영 반영 전
`internal.worker.api-key` private config와 외부 worker header 적용을 별도 gate로 확인한다.

---

## Task 6: v2 Refresh, logout과 실험용 v1 token endpoint 제거

**Files:**

- Replace or split: `src/main/java/com/poppang/be/domain/auth/application/TokenService.java`
- Delete: `src/main/java/com/poppang/be/domain/auth/presentation/TokenController.java`
- Delete when unused: legacy `AccessTokenResponseDto`, `TokenResponseDto`, `TokenRefreshRequestDto`
- Delete when unused: legacy `RefreshTokenRedisRepository` and implementation
- Create: `src/main/java/com/poppang/be/domain/auth/presentation/v2/V2TokenController.java`
- Create: `src/main/java/com/poppang/be/domain/auth/dto/v2/request/V2TokenRefreshRequestDto.java`
- Create: `src/main/java/com/poppang/be/domain/auth/dto/v2/response/V2TokenResponseDto.java`
- Test: `src/test/java/com/poppang/be/domain/auth/application/V2TokenServiceTest.java`
- Test: `src/test/java/com/poppang/be/domain/auth/presentation/v2/V2TokenControllerTest.java`

**Steps:**

- [x] 로그인용 Access/Refresh 동시 발급과 strict refresh rotation을 구현한다.
- [x] refresh 시 active+COMPLETED 사용자를 확인하고 새 sid가 아니라 같은 session의 새 Access/Refresh를
      원자적으로 교체한다.
- [x] logout은 principal userUuid+sid가 Redis 현재 session과 같을 때만 삭제하며 key 없음은 200으로
      처리한다. Redis 장애와 key 없음은 구분한다.
- [x] token 응답에 `Cache-Control: no-store`, `Pragma: no-cache`를 설정한다.
- [x] v1 inventory test에서 승인된 두 endpoint만 사라졌는지 확인한다.

**Verification:**

```bash
./gradlew test --tests 'com.poppang.be.domain.auth.*V2Token*'
./gradlew test --tests com.poppang.be.contract.V1EndpointInventoryContractTest
```

**Status:** 구현 청크 5 완료 (2026-07-31). Access/Refresh 동시 발급, 동일 session strict
rotation, active+COMPLETED 사용자 검증, idempotent logout과 인증 저장소 장애 구분을 구현했다.
승인된 v1 실험 endpoint 두 개만 제거했으며 관련 테스트를 포함한 전체 test 237개가
실패·오류·스킵 없이 통과했다. Entity/JPA/DB schema 변경은 없다.

---

## Task 7: V2 provider 검증과 v1 auth 구현 격리

**Files:**

- Create: `src/main/java/com/poppang/be/domain/auth/application/VerifiedSocialIdentity.java`
- Create: V2 provider별 credential verifier interface/implementation
- Do not modify: Kakao/Google/Apple v1 `*AuthServiceImpl.java`
- Additive only: `src/main/java/com/poppang/be/domain/users/infrastructure/UsersRepository.java`
- Test: provider별 V2 verifier test와 v1 auth 구현 격리·회귀 test

**Steps:**

- [x] V2 remote provider credential 검증과 DB 사용자 생성·token 발급 orchestration을 분리한다.
- [x] V2 login/signup만 `findByProviderAndUid`를 사용한다. v1의 기존 `findByUid` 호출은 유지한다.
- [x] v1 Controller·DTO·`*AuthServiceImpl`과 provider HTTP 요청 구현을 변경하지 않는다.
- [x] V2는 provider가 검증한 email만 신뢰하고 body의 uid/provider/role로 계정을 선택하지 않는다.
- [ ] 동일 provider+uid 동시 최초 로그인에서 하나의 사용자만 생성되는지 검증한다.
- [x] v2 Web OAuth callback은 제공하지 않고 세 provider의 GET login을 permitAll에서 제외한다.
      기존 v1 callback과 검증 코드는 호환성을 위해 유지한다.

**Verification:**

```bash
./gradlew test --tests 'com.poppang.be.domain.auth.*'
./gradlew test --tests com.poppang.be.contract.V1EndpointInventoryContractTest
./gradlew test --tests com.poppang.be.common.contract.V1AuthImplementationIsolationContractTest
```

---

## Task 8: v2 social login과 Signup Token 회원가입

**Files:**

- Create: `src/main/java/com/poppang/be/domain/auth/presentation/v2/V2AuthController.java`
- Create: `src/main/java/com/poppang/be/domain/auth/application/V2AuthOrchestrator.java`
- Create: `src/main/java/com/poppang/be/domain/auth/application/V2SignupWriter.java`
- Create: `src/main/java/com/poppang/be/domain/auth/dto/v2/request/*`
- Create: `src/main/java/com/poppang/be/domain/auth/dto/v2/response/*`
- Modify: `src/main/java/com/poppang/be/domain/users/entity/Users.java`
- Modify: keyword/recommend repositories needed for signup validation
- Test: `V2AuthOrchestratorTest`, `V2SignupConcurrencyTest`, `V2AuthControllerTest`

**Steps:**

- [ ] COMPLETED login은 Access/Refresh, 신규·PENDING login은 Signup Token만 반환한다.
- [ ] provider별 signup POST 세 개만 `TOKEN_SIGNUP`을 받고 body에서 uid/userUuid/provider/role/email을
      제거한다.
- [ ] row lock 안에서 PENDING/provider/active 상태, 닉네임 중복, keyword/recommend ID, FCM 값을
      검증한다.
- [ ] `Users row lock → 상태 재검증 → Redis latest hash consume → DB commit` 순서를 지킨다.
- [ ] commit 뒤 Access/Refresh를 발급하고 Redis 저장/응답 실패 시 재로그인으로 복구되게 한다.
- [ ] latest-only, 동시 signup, Redis timeout, consume 후 rollback, login/signup 경합을 테스트한다.
- [ ] Signup Token으로 닉네임·추천 보조 API나 일반/admin API를 호출하면 403인지 검증한다.
- [x] 로그인/signup/refresh rate limiter는 승인된 기본 임계값을 사용하고 key에 token,
      email, FCM token을 넣지 않는다.

**Verification:**

```bash
./gradlew test --tests 'com.poppang.be.domain.auth.*V2*'
./gradlew test --tests com.poppang.be.common.security.SecurityChainContractTest
```

**Status:** 구현 청크 6 완료 (2026-07-31). v2 Kakao는 모바일 login과 Signup Token 기반 signup
두 POST만 제공하며 브라우저 callback은 만들지 않는다. v1 `KakaoAuthServiceImpl`에 들어갔던
V2 verifier 공유 리팩터링은 사용자 결정에 따라 `main` 기준 구현으로 완전히 복원했고, V2
Kakao verifier와 service만 독립적으로 유지한다. Users 영속 field/JPA mapping/DB schema 변경
없이 기존 컬럼을 사용하는 additive state method만 사용한다. v2 인증 Rate Limit은 2026-07-31
승인된 기본값과 Redis 원자 script로 별도 구현했으며 v1에는 적용하지 않는다.

**Status:** 구현 청크 7 완료 (2026-07-31). v2 Google·Apple은 모바일 login과 Signup Token 기반
signup 네 POST만 제공하며 브라우저 callback은 만들지 않는다. V2 전용 provider credential
검증과 공통 PENDING/COMPLETED 분기, provider 일치 회원가입을 구현했고 v1
Google·Apple·Kakao 구현은 V2 코드와 격리한다. 전체 test 267개가 실패·오류·스킵 없이
통과했으며 Entity/JPA/DB schema 변경과 외부 DB·Redis·provider 접속은 없다. 실제 provider
실연동은 Wave 3 운영 배포 후 smoke gate로 남긴다. Apple nonce는 v1을 수정하지 않고 v2
`auth_code + raw_nonce` 요청과 ID Token nonce SHA-256 constant-time 대조로 구현했다.

---

## Task 9: v2 사용자 self-service

**Files:**

- Create: `src/main/java/com/poppang/be/domain/users/presentation/v2/V2UsersController.java`
- Create: `src/main/java/com/poppang/be/domain/users/dto/v2/*`
- Modify: `UsersService.java`, `UsersServiceImpl.java`, `Users.java`
- Test: `src/test/java/com/poppang/be/domain/users/presentation/v2/V2UsersControllerTest.java`
- Test: `src/test/java/com/poppang/be/domain/users/application/V2UsersServiceTest.java`

**Steps:**

- [x] `/api/v2/user/**`에서 path/body userUuid를 제거하고 principal만 사용한다.
- [x] user 조회, 알림 동의, 닉네임 검사·변경을 v1 응답 의미에 맞춰 구현한다.
- [x] FCM은 idempotent `PUT /api/v2/user/fcm-token` 하나만 제공하고 duplicate-check를 만들지 않는다.
- [x] `DELETE /api/v2/user`는 row를 보존하고 `is_deleted=true`로 변경한다. v2 hard-delete/restore는
      만들지 않는다.
- [x] commit 뒤 Refresh/Signup key 정리를 수행하고 실패 시 탈퇴 성공은 유지하되 안전한 로그를 남긴다.
- [x] 다른 사용자의 UUID를 path/body에 넣어도 대상을 바꿀 수 없는지 검증한다.

**Verification:**

```bash
./gradlew test --tests 'com.poppang.be.domain.users.*V2*'
./gradlew test --tests com.poppang.be.contract.V1EndpointInventoryContractTest
```

**Status:** 구현 청크 8 완료 (2026-07-31). Access Token principal만 사용하는 사용자 API 여섯
개를 v1과 분리해 구현했다. 본인 조회 응답에서 provider uid와 FCM token을 제외하고, v2 FCM
duplicate-check·hard-delete·restore는 만들지 않았다. 탈퇴는 Users row를 soft-delete한 transaction
commit 뒤 Refresh/Signup key를 정리하며, 인증 저장소 장애는 민감정보 없는 고정 로그를 남기고
탈퇴 성공을 유지한다. 전체 test 291개가 실패·오류·스킵 없이 통과했으며 Entity/JPA/DB schema
변경과 외부 DB·운영 Redis 접속은 없다. Wave 3는 운영 설정과 배포 직전 전체 build 검증을
통과한 뒤 배포한다. v2 로그인/signup/refresh Rate Limit은 승인된 기본값으로 구현하고 폐기형
Redis 동시성 검증을 수행한다.

**Wave 3 배포 전 로컬 gate (2026-07-31):** `clean build`와 전체 test 304개가 실패·오류·스킵
없이 통과했다. 배포 workflow가 가져오는 private `application-prod.yml`은 값을 출력하지 않고
YAML 문법, JWT 필수 7개 속성, 서로 다른 audience, 양수 TTL, 32자 이상 Worker API Key를
검증했다. v1 소셜 인증 Service·Controller·요청 DTO는 `origin/main`과 동일하다. 따라서 Wave 3
배포를 시작할 수 있으나, 운영 배포 완료 판정은 health, 대표 v1 익명 API, v2 401/403, 실제
Kakao·Google·Apple v2 로그인 smoke가 통과한 뒤에만 한다.

**Wave 3 운영 반영 (2026-07-31):** PR #8이 `main`에 merge되어 운영 배포가 성공했다. Actuator
health, 대표 v1 익명 API, token 없음·잘못된 token에 대한 v2 401 JSON 응답을 확인했다. 실제
Kakao·Google·Apple v2 로그인, 유효 Access Token, refresh와 403 흐름은 사용할 테스트 계정·token이
없어 운영 smoke를 수행하지 못했으며, 클라이언트 전환 전 확인 항목으로 유지한다.

---

## Task 10: v2 찜·키워드·알림함·개인화 팝업·제보

**Files:**

- Create: favorite/keyword/alert 도메인의 `presentation/v2` Controller와 `dto/v2`
- Create: popup 도메인의 v2 user popup/submission Controller와 DTO
- Create: 각 도메인의 별도 V2 application service/adapter에서 actor userUuid와 target을 분리
- Test: 각 v2 Controller principal/target/security test와 v1 service regression test

**Steps:**

- [x] 찜과 키워드 request/query의 caller userUuid를 제거하고 popupUuid/keyword만 유지한다.
- [x] 알림함 조회·삭제·읽음은 `/api/v2/user/alert/**`와 principal을 사용한다.
- [ ] `/api/v1/users/{userUuid}/popups/**`는 `/api/v2/user/popups/**`로 이동하고 principal 기반
      개인화 결과를 반환한다.
- [ ] popup 제보 body userUuid를 제거하고 principal을 `submitter_user_uuid` 감사 값으로 저장한다.
- [ ] v1 DTO, v1 service 구현과 기존 entry point를 제거·변경·공통화하지 않는다.
- [ ] 모든 v2 endpoint에 token 없음 401, Signup Token 403, 다른 사용자 override 불가를 검증한다.

**구현 청크 9 Status:** 완료 (2026-07-31). 찜 4개와 알림 키워드 3개를 v1과 분리된 v2
Controller·DTO·application service로 추가했다. 호출자 UUID는 request/query에서 제거하고 검증된
Access Token principal만 사용한다. 키워드 등록·삭제는 null·빈 문자열·공백 입력을 repository 접근
전에 `INVALID_USER_REQUEST`로 거절하고, 동일 사용자의 중복 키워드 등록은
`ALERT_KEYWORD_ALREADY_EXISTS`로 거절한다. 전체 test 329개와 `spotlessCheck`가 통과했으며 v1,
Entity/JPA/DB schema 변경과 외부 DB·Redis 접속은 없다.

**구현 청크 10 Status:** 완료 (2026-07-31). 알림함 조회·삭제·읽음 API 세 개를 v1과 분리된
v2 Controller·DTO·application service로 추가했다. 호출자 UUID는 path/body/query에서 제거하고
검증된 Access Token principal만 사용하며, 알림 등록 POST는 worker용 구현 청크 18 범위로 남겼다.
삭제·읽음 대상 popupUuid는 null·빈 문자열·공백을 repository 접근 전에
`INVALID_USER_REQUEST`로 거절한다. 메인 세션 재검수에서 보안·v1 호환 focused test 49개와 전체
test 346개가 실패·오류·스킵 없이 통과했고 `compileJava`, `spotlessCheck`, `git diff --check`도
통과했다. v1, Entity/JPA/Repository/DB schema 변경과 외부 DB·운영 Redis 접속은 없다.

**Wave 4 운영 반영 (2026-07-31):** PR #9가 merge commit `28bc9dee`로 `main`에 병합됐고,
GitHub Actions production run `30627748110`의 Main Verify, Build and Deploy Production,
Notify Result가 모두 성공했다. 운영 Actuator는 HTTP 200 `UP`, 대표 v1 익명 Web popup API는
HTTP 200, v2 favorite의 token 없음 요청은 HTTP 401 `AUTHENTICATION_REQUIRED`, v2 alert의
잘못된 Bearer Token 요청은 HTTP 401 `INVALID_TOKEN` 계열 응답임을 확인했다. 사용할 수 있는
운영 테스트 계정·유효 Access Token이 없어 찜·키워드·알림함의 정상 요청 smoke와 다른 사용자
접근 거절은 운영에서 실행하지 못했으며, 클라이언트 전환 전 확인 항목으로 유지한다.

**구현 청크 14 Status:** 7/7 구현·검증 완료 (2026-08-03).
`GET /api/v2/user/popups`, `/{popupUuid}`, `/upcoming`, `/search`, `/inProgress`, `/random`을 별도
v2 Controller·DTO·application service·mapper로 추가했다. caller userUuid는 제거하고 검증된
Access Token principal만 사용하며 v1의 사용자별 찜 여부, count boost, 검색·날짜·랜덤 광고 우선
계약을 유지한다. 최초 71개 inventory 이후 추가된 v1 무한 스크롤에 대응해 14-A에서
`GET /api/v2/user/popups/scroll`도 추가했다. 선택적 Long cursor, 15개 고정 페이지, 활성·미종료
팝업의 ID 내림차순 조회, 경량 item, 대표 이미지·찜 여부 배치 조회와 `nextCursor`·`hasNext` 계약은
v1과 같고, path userUuid만 Access Token principal로 대체했다. v1 Controller·DTO·Service·Mapper,
기존 Repository/query와 Entity/JPA/DB schema 변경은 없다. 청크 14-A focused test 27개와 확대
focused test 70개, 전체 test 431개가 실패·오류·스킵 없이 통과했고 `compileJava`,
`spotlessCheck`, `git diff --check`도 통과했다. 전체 v2 mapping은 72개다.

**구현 청크 15 Status:** 6/6 구현·검증 완료 (2026-08-03). 개인화 홈·지도 필터, 사용자 관심사
추천, 연관 팝업, 추천 카테고리 조회를 기존 청크 14의 v2 user popup 계층에 추가하고,
`POST /api/v2/popup-submissions`는 별도 v2 Controller·DTO·application service로 구현했다. 모든
caller userUuid 입력을 제거하고 Access Token principal만 사용한다. 제보 DTO는 userUuid를 노출하지
않으며 principal UUID를 기존 `submitter_user_uuid` 감사 값으로 저장한다. v1의 정렬·정규화·추천
보충·광고 우선 노출·찜 상태와 제보 유효성 검증·PENDING 저장·이미지 실패 정리 계약을 유지한다.
v1의 연관 팝업 랜덤 보충이 현재 조회 중인 popup을 제외 목록에 추가하지 않는 특성도 이번
마이그레이션에서는 바꾸지 않고 잔여 위험으로 기록한다. v1 Controller·DTO·Service·Mapper,
Repository/query와 Entity/JPA/DB schema 변경은 없다. 메인 focused test 88개와 전체 test 458개가
실패·오류·스킵 없이 통과했고 `compileJava`, `spotlessCheck`, `git diff --check`도 통과했다. 실제
운영 Access Token·DB 데이터·multipart 파일 저장·reverse proxy smoke는 미검증이다.

**Verification:**

```bash
./gradlew test --tests 'com.poppang.be.domain.favorite.*'
./gradlew test --tests 'com.poppang.be.domain.keyword.*'
./gradlew test --tests 'com.poppang.be.domain.alert.*'
./gradlew test --tests 'com.poppang.be.domain.popup.*V2*'
```

---

## Task 11: v2 일반 앱 조회·Web·Admin API

**Files:**

- Create: popup/recommend 도메인의 v2 app/web Controller와 DTO
- Create: `src/main/java/com/poppang/be/domain/popup/presentation/v2/V2PopupAdminController.java`
- Create: principal role을 전제로 하는 별도 V2 admin application service/adapter
- Test: v2 app/web/admin Controller 및 security test

**Steps:**

- [x] `/api/v2/popup/**`, `/api/v2/recommend/**`, 찜 수·조회수 같은 앱 조회도 TOKEN_ACCESS를
      요구한다.
- [x] 비활성 팝업 포함 같은 기존 동작은 JWT 마이그레이션에서 임의로 고치지 않고 v1 기능 계약을
      유지한다.
- [x] 기존 web popup GET을 `/api/v2/web/popup/**`로 twin 구현한다.
- [x] 추천 Web은 `/api/v2/web/recommend`만 만들고 `/api/v2/recommend/web`은 만들지 않는다.
- [x] `/api/v2/web/**`에 write method나 사용자/관리자 DTO가 없도록 architecture test를 작성한다.
- [ ] 모든 `/api/v2/admin/**`에서 query caller uuid를 제거하고 DB의 현재 ROLE_ADMIN을 요구한다.
- [ ] legacy에서 관리자 검사가 빠진 submission status endpoint도 v2에서는 동일하게 보호한다.

**구현 청크 11 Status:** 완료 (2026-08-03). 일반 popup 전체·상세·검색·오픈 예정·진행 중·지역/구·
랜덤 조회 7개를 `/api/v2/popup/**`의 별도 Controller·DTO·application service·mapper로 추가했다.
모든 endpoint는 Access Token을 요구하고 caller userUuid 입력을 받지 않는다. 기존 v1의 raw 응답
필드, 비활성 popup 포함, 검색·날짜·랜덤 조회 조건과 PopupCountBoost를 포함한 좋아요·조회수 계산을
유지했다. v1 Controller·DTO·Service·Repository와 Entity/JPA/DB schema 변경은 없다. 사이드 세션
전체 test 372개와 메인 검수 focused test 60개가 실패·오류·스킵 없이 통과했고 `spotlessCheck`,
`git diff --check`도 통과했다. 필수 검색어 `q` 누락 시 기존 예외 처리에 따라 HTTP 500을 반환하는
특성은 v1 호환을 위해 유지했다. 운영 DB 데이터와 유효 Access Token을 사용하는 정상 요청 smoke는
Wave 5 배포 후에도 미검증 항목으로 남아 있다.

**구현 청크 12 Status:** 완료 (2026-08-03). 일반 popup 필터 3개와 관련·카테고리·개인 추천 조회
3개를 기존 v1과 분리된 v2 Controller·application service로 추가했다. 개인 추천은 path의 caller
userUuid를 제거하고 검증된 Access Token principal을 사용한다. 관련 popup의 랜덤 보충 조회에서는
현재 popup과 이미 선택된 관련 popup을 모두 제외하며, 계약 테스트는 v2 popup Controller에 허용된
13개 mapping만 정확히 존재하는지 검증한다. v1 Controller·DTO·Service·Repository와
Entity/JPA/DB schema 변경은 없다. 전체 test 383개와 `spotlessCheck`, `git diff --check`가
통과했으며 외부 DB·Redis 접속과 애플리케이션 실행은 하지 않았다. 운영 DB 데이터와 유효 Access
Token을 사용하는 정상 요청 smoke는 Wave 5 배포 후에도 미검증 항목으로 남아 있다.

**구현 청크 13 Status:** 완료 (2026-08-03). 조회수 증가·총 조회수·Redis delta 조회 3개와 앱용
Recommend 전체·featured 조회 2개를 기존 v1과 분리된 v2 Controller·DTO·application service로
추가했다. 조회수는 기존 Redis key·원자적 INCR·70초 TTL을 유지하고, 총 조회수는 v1과 동일하게
DB 저장값과 viewCountBoost만 합산한다. Recommend featured 대상 ID 21과 raw list 응답 필드도
유지했다. 다섯 API는 Access Token을 요구하고 caller userUuid를 받지 않는다. 메인 세션 전체 test
395개와 `spotlessCheck`, `git diff --check`가 통과했다. v1 Controller·DTO·Service·Repository와
Entity/JPA/DB schema 변경, 외부 DB·운영 Redis 접속, 애플리케이션 실행은 없다. 운영 DB 데이터,
유효 Access Token, 운영 Redis INCR·TTL을 사용하는 정상 요청 smoke는 아직 미검증이다.

**Wave 5 운영 반영 (2026-08-03):** PR #11이 `main`에 merge됐고 merge commit은 `1fe6ffd`,
production run은 `30788767383`이다. Main Verify, Build and Deploy Production, Notify Result가 모두
성공했고 원격 배포의 신규 image health check와 외부 Actuator `UP`을 확인했다. 대표 v1 익명
`GET /api/v1/popup/regions/districts`는 HTTP 200, v2 무토큰 `GET /api/v2/popup`은 HTTP 401을
반환했다. rollback은 실행되지 않았고 Entity/JPA/DDL/DB 변경도 없다. 유효 Access Token으로 18개
v2 API의 정상 응답을 비교하는 smoke와 운영 Redis INCR·TTL 확인은 테스트 token 부재로 남아 있으며,
클라이언트 전환 전에 수행한다.

**구현 청크 16 Status:** 7/7 구현·검증 완료 (2026-08-04). 기존 공개 Web popup의 random,
favorite, in-progress, upcoming, search, detail 여섯 API와 Web Recommend 조회를 각각
`/api/v2/web/popup/**`, `/api/v2/web/recommend`에 별도 v2 Controller·DTO·application service로
추가했다. GET·HEAD는 익명 접근과 잘못된 Bearer Token 비개입을 유지하고, 같은 namespace의
write method는 401로 차단한다. `favorite`가 이름과 달리 조회수 상위 결과를 반환하는 계약을
포함해 v1 조회 조건·응답 의미를 그대로 유지했으며 `/api/v2/recommend/web`은 만들지 않았다.
메인 검수 focused test 103개가 실패·오류·스킵 없이 통과했다. v1 Controller·DTO·Service·Mapper,
Repository/query, Entity/JPA, SecurityConfig와 DB schema 변경은 없다. 운영 Web 응답, reverse
proxy, Authorization header 유무별 smoke와 실제 운영 데이터 비교는 Wave 6 배포 후 검증한다.
Wave 6의 청크 14~16 구현은 완료됐지만 운영 배포 완료 수는 5/7로 유지한다.

**Verification:**

```bash
./gradlew test --tests 'com.poppang.be.domain.popup.presentation.v2.*'
./gradlew test --tests 'com.poppang.be.domain.recommend.*V2*'
./gradlew test --tests com.poppang.be.common.security.SecurityChainContractTest
```

---

## Task 12: v2 internal worker API

**Files:**

- Create: popup/users/alert 도메인의 `presentation/v2/internal` Controller와 worker DTO
- Modify: polling projection/DTO가 Users.uuid를 반환하도록 v2 전용 query 추가
- Test: `src/test/java/com/poppang/be/internal/V2WorkerApiTest.java`

**Steps:**

- [ ] 설계 문서의 다섯 v2 internal mapping을 구현하고 모두 `X-Worker-Api-Key`를 요구한다.
- [ ] popup 등록·image upsert는 crawler target만 받고 caller user identity를 받지 않는다.
- [ ] 알림 대상 polling 응답은 내부 Long id 대신 Users.uuid를 반환한다.
- [ ] alert 생성의 path userUuid는 인증 주체가 아닌 recipient target으로 유지한다.
- [ ] API Key 누락/불일치 401, 일반 JWT 접근 거절, inactive user 제외, secret 로그 미노출을 검증한다.
- [ ] 외부 worker 코드 또는 access log로 alert POST 사용 여부를 확인한 결과를 migration matrix에 남긴다.

**Verification:**

```bash
./gradlew test --tests com.poppang.be.internal.V2WorkerApiTest
```

---

## Task 13: OpenAPI, 관측, migration matrix와 전체 회귀

**Files:**

- Modify: `src/main/java/com/poppang/be/common/config/OpenApiConfig.java`
- Create: v1/v2 SpringDoc group와 JWT/Signup/API Key security scheme 설정
- Create: 저카디널리티 route/auth metric interceptor 또는 filter
- Create: `docs/migrations/jwt-v2-endpoint-status.md`
- Modify if needed: `.github/workflows/build-test.yml`, `.github/workflows/cicd.yml`
- Test: OpenAPI/security/metric architecture contract tests

**Steps:**

- [ ] v1과 v2 OpenAPI group을 분리하고 public, Access, Signup, worker operation의 security scheme을
      실제 인가와 맞춘다.
- [ ] route version, normalized route, status, auth outcome만 metric label로 사용한다.
- [ ] UUID/token/email/FCM/API Key를 metric이나 로그에 포함하지 않는다.
- [x] 완료 청크의 v1 유지 내용과 v2 동작 차이를 기존 설계 문서에서 누적 기록하기 시작한다.
- [ ] migration matrix에 모든 v1 endpoint의 v2 대체, iOS/AOS/ETL version, v1 최근 호출,
      rollback 확인, 삭제 가능 상태를 기록할 열을 만든다.
- [ ] CD가 `spotlessCheck`와 `clean build`를 통과한 동일 JAR로 image를 만드는지 확인한다.
- [ ] 마지막 변경 뒤 focused test, 전체 test, formatting, clean build를 새로 실행한다.

**Verification:**

```bash
./gradlew spotlessCheck
./gradlew clean test
./gradlew clean build
```

**Exit gate:** v1 회귀, v2 security, 실제 Redis 동시성 test가 모두 통과하고 미검증 항목이 명시되어야
구현 완료라고 보고할 수 있다.

---

## Deployment and migration sequence

1. 1/7의 구현 청크 0을 별도 PR로 merge·배포해 v1 기준선을 먼저 고정한다.
2. 로컬 복원 DB에서 audit/expand SQL 결과를 확인한다. DB-E1은 사용자 결정에 따라 로컬 MySQL
   9.2.0 결과를 사용하며 운영과 같은 major의 재연습은 생략한다.
3. DB-E1의 대상 DB, backup, DDL, lock 영향과 rollback을 보고하고 별도 승인을 받는다.
4. 승인된 expand SQL을 적용·검증하고 v1 signup 상태 전이를 보완한 구현 청크 1을 별도 PR로
   merge·배포한다. 두 선행 배포를 합쳐 1/7 기반 wave 완료로 기록한다.
5. private config에 JWT audience/TTL과 worker API Key를 별도 승인으로 준비한다. Rate Limit은
   승인된 코드 기본값을 사용하며 운영에서 다른 값이 필요할 때만 private config로 재정의한다.
6. 2/7~7/7을 위 표 순서대로 각각 검증·merge·운영 배포하고, 구현 청크마다 commit 후보를 만들되
   운영 배포는 wave 경계에서만 수행한다. 각 wave 뒤 v1 smoke와 해당 v2 smoke를 실행하고, 실패하면
   다음 wave를 중단한 뒤 직전 검증 image로 rollback한다.
7. 필요한 서버 wave가 모두 안정화된 뒤 iOS와 Android를 v2로 전환하고 version별 전환율과 v1
   route 호출을 관찰한다.
8. 외부 ETL/notification worker를 API Key와 v2 internal 경로로 전환한다.
9. 최소 지원 앱 버전을 올리고 정한 관찰 기간 동안 v1 route 호출 0건을 확인한다.
10. no-return 승인을 받은 뒤 contract SQL로 SignupStatus/identity 제약을 강화한다.
11. v1 삭제는 이 구현과 분리된 별도 change, 별도 commit, 별도 배포로 수행한다.

## Completion definition

- 두 실험용 token endpoint 외 모든 v1 API가 기존 클라이언트에서 계속 동작한다.
- 모든 v2 앱 API는 Access Token 없이는 접근할 수 없고 본인 identity를 위조할 수 없다.
- Signup/Refresh Token은 용도와 Redis 최신 상태가 분리되고 동시 요청에서 한 번만 소비된다.
- 관리자와 worker 경계가 각각 ROLE_ADMIN과 전용 API Key로 강제된다.
- iOS/AOS/ETL migration status를 endpoint 단위로 기록할 수 있다.
- commit, push, DDL, 배포는 각각 필요한 사용자 승인을 받기 전에는 실행하지 않는다.
