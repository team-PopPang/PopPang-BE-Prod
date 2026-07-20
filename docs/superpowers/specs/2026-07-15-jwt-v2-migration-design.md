# v2 JWT 인증 및 API 마이그레이션 설계

## 상태

`APPROVED`. 인증 계약과 점진 배포 정책은 승인됐으며 구현은 진행 중이다. 구현·DB 적용·운영
배포 완료 여부는 구현 체크리스트에서 별도로 관리한다.

## 문서 목적

현재 공개 상태인 v1 API를 중단하지 않고, JWT 인증·인가가 적용된 v2 API를 추가한다.
iOS, Android, ETL 전환이 모두 확인될 때까지 v1을 유지하고, 실제 v1 호출이 사라진 뒤 별도
작업으로 제거한다.

회원가입과 Signup Token의 상세 흐름은
[2026-07-15-signup-token-flow-design.md](./2026-07-15-signup-token-flow-design.md)를 함께 따른다.
두 문서가 충돌하면 이 문서에서 나중에 확정한 정책을 우선한다.

## 최우선 원칙

가장 중요한 요구사항은 v2를 구현하는 동안 현재 v1 API가 계속 동일하게 동작하는 것이다.

- v1 URL, HTTP method, query, path, body, 응답 JSON, 상태 코드, media type을 바꾸지 않는다.
- v1의 오타 경로, 단수형 경로, 혼재된 응답 형식도 마이그레이션 중에는 유지한다.
- v2는 별도 Controller, DTO, SecurityFilterChain으로 추가한다.
- 공통 Service나 Entity를 수정할 때는 v1 회귀 테스트를 먼저 작성한다.
- DB 변경은 롤백 가능한 추가·보정 방식으로 진행하고 기존 컬럼을 바로 삭제하지 않는다.
- Entity/JPA/schema 영향이 예상되면 코드 수정 전에 영향과 DDL을 제시하고 사용자의 명시적 승인을
  받는다. 일반적인 작업 진행 승인은 Entity/DDL 승인으로 간주하지 않는다.
- `main` merge는 운영 자동 배포를 시작하므로 JWT 전체를 한 PR로 합치지 않고 독립 배포 가능한
  wave로 나눈다. 각 merge는 push 승인과 별도로 운영 배포 승인을 다시 받는다.
- iOS/AOS 코드 전환만으로 v1을 삭제하지 않는다. 구버전 앱 지원 종료와 실호출 0건을 확인한다.

다음 두 endpoint는 실제 서비스 계약이 아닌 과거 JWT 실험용이므로 호환 원칙의 명시적
예외로 삭제한다. 이는 2026-07-15 설계 논의에서 사용자가 용도를 확인하고 승인한 예외이며,
다른 v1 endpoint 삭제 승인으로 확대 해석하지 않는다.

- POST /api/v1/auth/token/test
- POST /api/v1/auth/refresh

## 현재 상태와 위험

- SecurityConfig가 사실상 모든 URL을 permitAll로 허용한다.
- JwtAuthenticationFilter는 Bearer Token이 있을 때만 principal을 채우며, 토큰이 없어도 요청을
  차단하지 않는다.
- 소셜 로그인 응답과 자체 JWT 발급이 연결되어 있지 않다.
- v1의 사용자 종속 API는 path, query, body의 userUuid를 호출자 신원으로 신뢰한다.
- 관리자 API 일부는 query의 uuid를 관리자 신원으로 사용하며 권한 검사가 누락된 API도 있다.
- refresh token은 Redis에 사용자당 하나가 저장되지만 rotation과 logout 흐름이 없다.
- provider가 다른 사용자를 전역 uid 하나로 조회하고, Users.uuid의 DB unique 보장이 명확하지
  않다.
- 외부 worker가 사용자 정보와 FCM token을 조회하는 endpoint도 공개되어 있다.

## 마이그레이션 중 잔여 위험

v2를 배포해도 동일한 기능의 공개 v1 endpoint가 남아 있으면 공격자는 v1을 호출해 인증을
우회할 수 있다. 이 문제는 signup과 worker뿐 아니라 사용자 변경·탈퇴·찜·알림·관리자 쓰기
API 전체에 적용된다.

따라서 다음 두 완료 상태를 구분한다.

- v2 구현 완료: v2 인증·인가와 기능 구현 및 test가 끝난 상태
- 인증 마이그레이션 완료: 대응 v1 우회 경로까지 종료되어 시스템 전체에 인증 경계가 생긴 상태

v1 호환을 위해 이 잔여 위험을 전환 기간 동안 수용하되, 관리자 상태 변경, hard-delete,
회원가입, FCM/worker 개인정보, popup 등록·수정처럼 피해가 큰 v1 endpoint를 우선 전환·종료
목록으로 관리한다. 전환 기간에는 route별 호출량과 비정상 요청을 관찰하고 가능한 인프라
범위에서 rate limit 또는 WAF를 적용한다.

## 확정된 정책 요약

| 항목 | 결정 |
|---|---|
| 앱 v2 기본 정책 | TOKEN_ACCESS 필요 |
| 공개 v2 | /api/v2/web/**와 정확히 지정한 로그인·refresh 경로 |
| 회원가입 | TOKEN_SIGNUP 필요 |
| 관리자 | TOKEN_ACCESS와 ROLE_ADMIN 모두 필요 |
| worker | /api/v2/internal/**와 전용 API Key |
| v1 | 별도 chain에서 기존 공개 API 유지 |
| Access Token | 15분 |
| Refresh Token | 30일, 재발급 시 다시 30일 |
| Signup Token | 15분 |
| Refresh 세션 수 | 사용자당 1개 |
| Refresh rotation | 엄격한 rotation, 이전 토큰 즉시 무효화 |
| 로그아웃 | Refresh Token 삭제, Access Token은 자연 만료 |
| v2 본인 탈퇴 | DELETE /api/v2/user, JWT principal 기준 soft-delete |
| v2 hard-delete | 앱용 endpoint를 만들지 않음 |
| 다중 기기 | 첫 버전에서는 지원하지 않음 |
| v1 삭제 | iOS/AOS/ETL 전환과 실호출 0건 확인 후 별도 수행 |

앱은 로그인 후에만 탐색할 수 있다는 현재 제품 결정을 따른다. 따라서 v1에서 익명 호출이
가능했던 popup·recommend·찜 수·조회수 같은 앱용 조회도 v2에서는 TOKEN_ACCESS가 필요하다.
첫 버전에는 token 유무에 따라 응답이 달라지는 optional-auth 앱 API를 만들지 않는다. 공개
조회가 필요하면 `/api/v2/web/**`의 별도 DTO·Controller로 제공한다.

## 보안 체인

보안 설정은 경로별 책임을 분리한 여러 SecurityFilterChain으로 구성한다.

~~~text
1순위  /api/v2/internal/**
       WorkerApiKeyAuthenticationFilter
       SERVICE_WORKER 필요

2순위  /api/v2/**
       V2 JwtAuthenticationFilter
       공개 경로                → permitAll
       provider별 signup       → TOKEN_SIGNUP
       /api/v2/admin/**        → TOKEN_ACCESS + ROLE_ADMIN
       그 밖의 /api/v2/**      → TOKEN_ACCESS

3순위  /api/v1/**
       기존 공개 API 유지
       v2 JWT filter를 적용하지 않음

4순위  인프라 경로와 나머지 요청
       health와 실제 SpringDoc 경로만 명시적으로 허용
       나머지는 denyAll
~~~

denyAll 전환 전에 `/error`, 실제 Actuator·SpringDoc custom path, Swagger UI asset, 정적 제보 이미지
경로와 reverse proxy가 처리하는 경로를 inventory한다. API가 아닌 기존 운영 경로까지 우연히
차단하지 않도록 현재 HTTP 동작을 characterization test로 고정하고, 필요한 경로만 HTTP method와
함께 명시적으로 허용한다.

permitAll 경로는 URL 인가 규칙만 통과시키는 것으로 끝내지 않는다. v2 JWT filter의
shouldNotFilter에서도 정확한 로그인, refresh, web 경로를 제외한다. 만료된 Access Token을
자동 첨부한 클라이언트가 refresh endpoint에 도달하지 못하는 문제를 막기 위해서다.

v2 JWT filter와 worker filter는 Servlet container에 자동 등록하지 않는다. 각 filter는 명시적
order와 securityMatcher를 가진 해당 SecurityFilterChain 안에서만 실행한다. 공개 경로
authorization과 shouldNotFilter는 같은 RequestMatcher 정의를 공유해 목록이 어긋나지 않게 한다.

공개 경로는 넓은 /auth/** 패턴으로 열지 않고 HTTP method와 정확한 경로를 명시한다. 새 endpoint가
추가되었을 때 별도 설정을 빠뜨리면 기본 TOKEN_ACCESS 정책에 의해 보호되어야 한다.

v2에서 익명 접근을 허용할 인증 경로는 다음으로 제한한다.

- GET /api/v2/auth/kakao/login
- GET /api/v2/auth/google/login
- GET /api/v2/auth/apple/login
- POST /api/v2/auth/kakao/mobile/login
- POST /api/v2/auth/google/mobile/login
- POST /api/v2/auth/apple/mobile/login
- POST /api/v2/auth/refresh
- /api/v2/web/**

provider별 signup과 logout은 이 목록에 포함하지 않는다. signup은 TOKEN_SIGNUP, logout은
TOKEN_ACCESS가 필요하다.

/api/v2/web/**는 공개 전용 namespace로 취급한다. 이 경로에는 GET·HEAD 기반 공개 조회만 둘 수
있고 사용자 정보, 관리자 기능, 상태 변경 endpoint를 추가하지 않는다. architecture test가
web 경로의 write method와 보호 대상 response type을 거절해야 한다.

legacy 예외인 `GET /api/v1/recommend/web`은 마이그레이션 동안 그대로 유지하되, v2에서는
`GET /api/v2/web/recommend`로 정규화한다. `/api/v2/recommend/web` 호환 별칭은 만들지 않는다.
기존 popup 웹 조회는 `/api/v1/web/popup/**`에서 `/api/v2/web/popup/**`로 버전만 바꾼 twin을
제공한다.

## JWT 계약

JWT는 기존 JwtProvider를 확장해 한 번만 생성·파싱한다. 다른 계층에서 Jwts 파싱 코드를
중복 작성하지 않는다.

### 공통 claim

| claim | 값 | 목적 |
|---|---|---|
| sub | 내부 Users.uuid | 인증된 사용자 식별 |
| iss | 설정된 PopPang issuer | 발급자 검증 |
| aud | token 용도별 audience | 과거 토큰과 용도 분리 |
| iat | 발급 시각 | 생성 시각 기록 |
| exp | token별 만료 시각 | 유효기간 제한 |
| jti | 매 발급마다 새 UUID | 같은 초에 발급해도 서로 다른 토큰 보장 |
| sid | Access/Refresh가 공유하는 session UUID | 이전 session과 현재 session 구분 |
| typ | ACCESS, REFRESH, SIGNUP | token 종류 구분 |

role, email, nickname, FCM token처럼 변경되거나 민감한 값은 JWT에 넣지 않는다. Access Token을
검증할 때 DB에서 활성 사용자와 현재 role을 조회한다. 권한 변경과 탈퇴 상태는 다음 요청부터
반영된다.

Signup Token에는 sid를 넣지 않는다. 로그인 또는 회원가입 완료 시 Access/Refresh 한 쌍에
동일한 새 sid를 부여한다.

### token별 정책

| token | audience | 만료 | SecurityContext authority |
|---|---|---:|---|
| Access | poppang-app-v2 | 15분 | TOKEN_ACCESS + 현재 DB role |
| Refresh | poppang-app-v2 | 30일 | 생성하지 않음 |
| Signup | poppang-signup-v2 | 15분 | TOKEN_SIGNUP |

v2 parser는 audience를 필수로 검증한다. 과거 실험용 JWT에는 v2 audience가 없으므로 동일한
서명 secret을 사용하더라도 v2 요청에 사용할 수 없다.

JWT header의 algorithm은 HS256만 허용하고 none 또는 예상하지 않은 algorithm은 거절한다.
서명과 algorithm을 먼저 검증한 claims에 대해서만 typ과 audience의 허용 조합을 검사한다.
검증되지 않은 payload의 typ을 먼저 읽어 parser나 audience를 선택하지 않는다.

private config에는 최소한 다음 설정이 필요하다.

- jwt.secret
- jwt.issuer
- jwt.audience
- jwt.signup-audience
- jwt.access-token-exp-minutes
- jwt.refresh-token-exp-days
- jwt.signup-token-exp-minutes
- internal.worker.api-key

HS256 secret은 최소 256 bit 이상의 예측 불가능한 값이어야 한다. 서버는 시작할 때 secret
길이와 필수 설정을 검증하고 조건을 충족하지 않으면 실행을 중단한다. 설정값 자체는 로그에
출력하지 않는다.

## authority와 token type

Signup Token도 서명을 통과하면 인증 객체가 만들어지므로 단순 authenticated 조건만 사용하면
일반 API에 접근할 수 있다. 따라서 token 용도를 authority로 분리한다.

| 요청 | 필요한 authority |
|---|---|
| provider별 회원가입 POST | TOKEN_SIGNUP |
| 일반 앱 v2 API | TOKEN_ACCESS |
| 관리자 v2 API | TOKEN_ACCESS + ROLE_ADMIN |
| worker v2 API | SERVICE_WORKER |

Refresh Token은 Bearer 인증에 사용하지 않는다. refresh endpoint의 body로만 받고 TokenService가
직접 검증한다.

`TOKEN_SIGNUP` 허용 목록은 Kakao·Google·Apple의 provider별 회원가입 POST 세 개로만 고정한다.
현재 앱은 가입 완료 전에 닉네임 중복 확인이나 추천 목록 조회 API를 별도로 호출하지 않으므로,
이런 보조 endpoint를 Signup Token용으로 열거나 `permitAll`로 두지 않는다. 닉네임 중복과 추천
ID 유효성은 회원가입 POST 내부에서 검증하고 잘못된 입력은 그 응답에서 명시적인 4xx로 알린다.

## 소셜 로그인과 회원가입

provider 연동 코드가 v1과 v2에 복제되지 않도록 다음 경계를 둔다.

~~~text
ProviderCredentialVerifier
  → provider credential 검증
  → VerifiedSocialIdentity(provider, uid, verifiedEmail) 반환

V1 legacy orchestration
  → 기존 v1 응답 계약 유지

V2 auth orchestration
  → SignupStatus 분기
  → Signup 또는 Access/Refresh Token 발급
~~~

ProviderCredentialVerifier는 DB 사용자 생성이나 token 발급을 하지 않는다. v1과 v2 orchestration은
검증 결과만 받아 각 버전의 계약을 처리한다.

### 소셜 로그인

1. permitAll endpoint가 Kakao, Google, Apple credential을 받는다.
2. provider SDK 또는 API를 통해 credential을 검증한다.
3. 검증 결과의 provider와 uid 조합으로 내부 사용자를 조회한다.
4. 삭제 또는 비활성 사용자는 token을 발급하지 않는다.
5. SignupStatus에 따라 응답을 나눈다.

| 사용자 상태 | 응답 |
|---|---|
| COMPLETED | Access + Refresh Token |
| 신규 | PENDING 사용자 생성 후 Signup Token |
| PENDING | 기존 사용자에 대한 새 Signup Token |
| 삭제·비활성 | ACCOUNT_NOT_ACTIVE 오류 |

브라우저 OAuth 경로를 제공할 때는 state를 검증하고 provider가 지원하면 PKCE와 nonce를
사용한다. 모바일 token은 provider별 issuer, audience, signature, nonce를 검증한다. 로그인,
signup, refresh endpoint에는 요청 크기 제한과 IP·provider uid 기반 rate limit을 적용한다.

email은 provider가 검증한 값만 저장한다. Apple처럼 후속 로그인에서 email을 다시 주지 않는
경우 기존 값을 유지하며 client body의 email로 덮어쓰지 않는다. 신규 사용자인데 검증된
email이 없다면 null을 허용하고 unverified email을 받지 않는다. 신규 role은 서버가 MEMBER로
고정한다.

같은 provider와 uid의 최초 로그인이 동시에 들어오면 DB unique 위반을 계정 연결로 오인하지
않고, 동일 provider+uid row를 재조회해 하나의 PENDING 사용자만 사용한다.

### 회원가입

Signup Token은 회원가입 화면 진입만으로 발급하지 않는다. 검증된 소셜 로그인 직후 신규 또는
PENDING 사용자에게만 발급한다.

v2 회원가입 body에는 다음 호출자 신원 필드를 받지 않는다.

- uid
- userUuid
- provider
- role
- 소셜 제공자가 이미 검증한 email

JWT sub의 Users.uuid만 가입 대상을 결정한다. provider 경로와 DB provider가 다르면 거절한다.
회원가입 transaction은 대상 Users row를 쓰기 잠금으로 조회한 뒤 PENDING을 다시 확인한다.
같은 Signup Token의 동시 요청 중 하나만 가입을 완료하고, 나머지는 잠금 해제 후 COMPLETED를
확인해 SIGNUP_ALREADY_COMPLETED를 반환한다.

Signup Token은 사용자별 Redis key `auth:v2:signup:{userUuid}`에 최신 token의 SHA-256 hash,
jti, issuedAt을 JWT exp까지 남은 TTL로 저장한다. PENDING 재로그인은 이 key를 덮어써 이전
token을 즉시 무효화한다. 회원가입은 사용자 row를 잠근 상태에서 제출 hash를 비교하고 key를
원자적으로 삭제한 요청만 진행한다. Redis 장애에는 발급과 회원가입 모두 503으로 fail-closed하며,
사용자 삭제·비활성 처리 시 Signup key도 삭제한다.

발급과 가입의 잠금 순서는 모두 `Users row write lock → 상태 재검증 → Redis`다. latest key는
hash와 만료를 하나의 `SET ... EXAT/PXAT` 명령으로 기록한다. JWT filter는 검증에 사용한 정확한
compact token의 SHA-256 fingerprint와 검증된 jti만 transient Authentication details로 전달하며,
서비스는 raw header를 다시 파싱하지 않는다. compare-delete Lua는 principal sub로 만든 key의
hash와 jti가 모두 일치할 때만 삭제한다.

가입 성공 시 같은 DB transaction에서 프로필·키워드·추천 정보와 SignupStatus=COMPLETED를
저장한다. DB commit 이후 Access/Refresh Token을 발급하고 Refresh Token을 Redis에 저장한다.

transaction 경계는 non-transactional V2AuthOrchestrator와 별도 bean의 transactional writer로
분리한다. Access/Refresh Token은 writer method가 정상 반환되어 Spring proxy의 commit이 완료된
뒤에만 orchestrator가 발급·저장한다. self-invocation으로 transaction method를 호출하지 않는다.

Signup Token 발급은 latest-only 순서를 보장하기 위한 예외다. transactional writer가 같은 사용자
row를 잠근 상태에서 PENDING row를 저장·flush하고 token을 만든 뒤 Redis 최신 hash를 기록한다.
raw token은 proxy commit이 끝난 뒤 orchestrator가 응답한다. Redis 기록 후 DB commit이 실패한
경우 반환되지 않은 hash는 TTL로 만료되거나 다음 로그인에서 덮어쓴다.

Redis SET 또는 compare-delete timeout은 명령 실행 여부가 불명확하므로 자동 재시도하지 않는다.
DB transaction을 rollback하고 503을 반환하며, client는 소셜 로그인부터 다시 수행한다.

## HTTP token 계약

### 로그인 응답

가입 완료 사용자는 다음 정보를 받는다.

~~~json
{
  "signupStatus": "COMPLETED",
  "tokenType": "Bearer",
  "user": {
    "userUuid": "...",
    "provider": "KAKAO",
    "email": "verified@example.com",
    "nickname": "팝팡",
    "role": "MEMBER",
    "isAlerted": true
  },
  "accessToken": "...",
  "refreshToken": "...",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 2592000
}
~~~

가입 대기 사용자는 다음 정보를 받는다.

~~~json
{
  "signupStatus": "PENDING",
  "tokenType": "Bearer",
  "signupToken": "...",
  "signupTokenExpiresIn": 900
}
~~~

expiresIn은 초 단위다. 실제 응답은 기존 ApiResponse data 안에 담는다.
COMPLETED 로그인과 signup 성공은 위와 같은 profile+token schema를 함께 사용한다. uid와
fcmToken은 login response에서 제외한다. refresh 성공은 user와 signupStatus 없이 tokenType,
새 Access/Refresh Token, 두 expiresIn만 반환한다.

로그인, signup, refresh처럼 token을 반환하는 모든 응답에는 Cache-Control: no-store와
Pragma: no-cache를 설정한다. production에서는 HTTPS가 아니면 token endpoint를 제공하지 않는다.

### 일반 API와 회원가입

~~~http
Authorization: Bearer {accessToken 또는 signupToken}
~~~

JWT typ과 authority로 token 용도를 구분한다.

### Refresh

~~~http
POST /api/v2/auth/refresh
Content-Type: application/json
~~~

~~~json
{
  "refreshToken": "..."
}
~~~

성공 시 새 Access Token과 새 Refresh Token을 모두 반환한다. 이전 Refresh Token은 즉시
무효화된다.

### Logout

~~~http
POST /api/v2/auth/logout
Authorization: Bearer {accessToken}
~~~

서버는 해당 사용자의 v2 Refresh Token을 삭제하고 200 OK와 빈 body를 반환한다. Access Token
denylist는 만들지 않으므로 이미 발급된 Access Token은 최대 15분 뒤 자연 만료된다.

logout은 Access Token의 userUuid와 sid가 Redis의 현재 session과 모두 같을 때만 key를 삭제한다.
이전 로그인에서 발급된 Access Token의 늦은 logout이 새 로그인 session을 삭제해서는 안 된다.
key가 없거나 sid가 이미 바뀐 logout도 idempotent하게 200을 반환한다.

## Refresh Token과 Redis

### 저장 구조

~~~text
key   = auth:v2:refresh:{userUuid}
value = { sid, refreshTokenHash, issuedAt }
TTL   = 30일
~~~

Redis 유출 시 원문 Refresh Token이 바로 노출되지 않도록 token hash를 저장한다. token 원문,
hash, API Key는 로그에 남기지 않는다.

사용자당 key 하나만 사용한다. 새로운 로그인이나 회원가입 완료가 같은 key를 덮어쓰므로 마지막
로그인만 refresh할 수 있다. 다른 기기의 기존 Access Token은 최대 15분 동안만 남는다.

기존 auth:refresh:{userUuid} key는 v2에서 사용하지 않는다. 운영 상태를 확인한 뒤 TTL 만료를
기다리거나 통제된 정리 작업으로 제거한다.

### 엄격한 rotation

1. Refresh JWT의 서명, issuer, audience, exp, typ, sid를 검증한다.
2. sub로 활성 상태의 COMPLETED 사용자를 조회한다.
3. 제출 token의 sid와 hash를 Redis의 현재 sid와 hash에 비교한다.
4. 새 jti를 가진 Access/Refresh Token을 만든다.
5. 같은 sid를 유지한 채 Redis에서 기존 hash 비교와 새 hash 저장을 하나의 원자 연산으로
   수행한다.
6. 성공한 요청만 새 token 쌍을 반환한다.

비교와 저장을 따로 실행하면 같은 Refresh Token의 동시 요청이 모두 성공할 수 있다. Redis Lua
script 또는 동등한 atomic compare-and-replace를 사용한다.

같은 Refresh Token으로 동시 요청하면 정확히 하나만 성공한다. 앱은 refresh single-flight를
구현해 하나의 refresh 요청만 보내고 다른 API 요청은 그 결과를 기다려야 한다.

서버가 rotation을 완료한 뒤 응답이 유실되면 이전 token은 복구하지 않는다. 클라이언트는 소셜
로그인을 다시 수행한다. 첫 버전에는 이전 token 유예 시간을 두지 않는다.

REFRESH_TOKEN_MISMATCH는 요청만 거절하고 Redis의 현재 session을 삭제하지 않는다. 응답 유실,
이전 기기, 공격 token을 구분할 수 없는 상태에서 현재 session을 삭제하면 제3자가 반복적으로
사용자를 로그아웃시킬 수 있기 때문이다. 정상 사용자가 소셜 로그인하면 새로운 sid와 Refresh
Token으로 현재 session을 덮어쓴다.

30일은 sliding expiration이다. 정상 rotation마다 TTL이 다시 30일로 설정되므로 활동 중인
사용자의 절대 session 최대 수명은 첫 버전에서 제한하지 않는다.

## 모바일 클라이언트 계약

- Access Token은 일반 v2 API의 Authorization Bearer header에 넣는다.
- Refresh Token은 refresh endpoint body에만 넣는다.
- iOS는 Keychain, Android는 Keystore 기반 암호화 저장소를 사용한다.
- token을 UserDefaults, 평문 SharedPreferences, 로그, 분석 이벤트에 남기지 않는다.
- 일반 API에서 EXPIRED_TOKEN을 받았을 때만 refresh를 한 번 시도한다.
- refresh endpoint의 401은 token을 모두 삭제하고 소셜 로그인으로 이동한다.
- 403에는 refresh를 시도하지 않는다.
- refresh 성공 후 새 token 쌍을 저장한 뒤 원래 API를 최대 한 번만 재시도한다.
- Signup Token은 가입 완료·취소·만료 시 삭제한다.

## v2 회원 탈퇴

Git 이력상 v1은 soft-delete 탈퇴를 hard-delete로 교체했지만, 실제 iOS/AOS 전환이 끝나기 전에는
세 legacy endpoint를 모두 그대로 유지한다.

- DELETE /api/v1/user/{userUuid}/hard-delete
- PATCH /api/v1/user/{userUuid}/soft-delete
- PATCH /api/v1/user/{userUuid}/resotre

v2에서는 과거 hard-delete 동작을 복제하지 않는다. 본인 탈퇴 계약은 다음 하나다.

~~~http
DELETE /api/v2/user
Authorization: Bearer {accessToken}
~~~

- TOKEN_ACCESS가 필요하다.
- request의 path, query, body에서 userUuid를 받지 않고 JWT principal만 사용한다.
- Users row를 삭제하지 않고 `is_deleted=true`로 변경한다.
- 찜, 키워드, 알림 이력, 추천, 소셜 식별 정보는 물리 삭제하지 않는다.
- 성공 응답은 기존 convention에 맞춰 200 OK와 빈 body다.
- v2 앱용 hard-delete와 v2 self-restore endpoint는 첫 버전에 만들지 않는다.

DB의 `is_deleted`가 인증 상태의 source of truth다. v2 JWT filter, social login, refresh는 매번
삭제 상태를 확인하고 탈퇴 사용자에게 Access/Refresh Token을 발급하거나 인증을 만들어 주지
않는다. 따라서 이미 발급된 Access Token도 다음 요청부터 거절한다.

첫 버전에는 Users.authVersion이나 Access Token blacklist를 추가하지 않는다. 공개 v1 restore가
탈퇴를 되돌리면 탈퇴 전에 발급된 Access Token이 원래 만료 시각까지 다시 유효해질 수 있지만,
Access Token의 최대 잔여 시간이 15분이고 v2 self-restore를 제공하지 않는 점을 고려해 전환 기간의
잔여 위험으로 수용한다. 이 위험 때문에 v1 restore 호출량을 별도로 관찰하고 v2 강제 전환 후
우선 제거한다.

탈퇴 DB transaction이 commit된 뒤 `auth:v2:refresh:{userUuid}`와
`auth:v2:signup:{userUuid}`를 삭제한다. Redis 정리가 실패해도 DB에서 탈퇴 상태를 확인하므로
token 사용은 거절된다. 탈퇴 자체는 성공으로 유지하고 정리 실패를 metric과 alert로 남기며,
남은 key는 재시도 작업 또는 TTL로 제거한다.

v1 공개 `hard-delete`와 `resotre`가 남아 있는 동안에는 v2 탈퇴 정책을 우회하거나 되돌릴 수
있는 legacy 위험이 계속된다. iOS/AOS를 v2로 강제 전환하고 정한 관찰 기간 동안 호출 0건을
확인한 뒤 세 v1 계정 상태 endpoint를 함께 제거한다. 보존 기간 이후 물리 삭제나 익명화가
필요하면 앱 API가 아닌 별도 통제 작업으로 설계한다.

## v2 Controller와 identity

v1 Controller에 v2 mapping을 함께 추가하지 않는다. 버전별 presentation adapter를 분리한다.

~~~text
v1 Controller + v1 DTO
       └─ legacy path/query/body userUuid

v2 Controller + v2 DTO
       └─ Authentication principal의 userUuid

두 adapter
       └─ 가능한 범위에서 동일한 application service 재사용
~~~

기존 Service가 v1 DTO 안의 userUuid를 직접 읽으면 기존 method를 깨뜨리지 않는다. actor
userUuid와 target resource를 분리한 새 command 또는 overload를 추가하고 v2가 이를 사용한다.

endpoint family별 v2 목적지는 다음과 같이 고정한다. 세부 query·body·response는 identity와
아래에 명시한 예외 외에는 v1 계약을 유지한다.

| v1 endpoint family | v2 endpoint family | 인가 | identity 처리 |
|---|---|---|---|
| `/api/v1/auth/{provider}/login`, `/mobile/login` | 같은 suffix의 `/api/v2/auth/**` | permitAll | 검증된 provider credential |
| `/api/v1/auth/{provider}/signup` | 같은 suffix의 `/api/v2/auth/**` | TOKEN_SIGNUP | Signup Token principal |
| `/api/v1/auth/autoLogin` | v2 twin 없음 | 해당 없음 | Access 검증·refresh로 대체 |
| `/api/v1/user/{userUuid}/**` | `/api/v2/user/**` | TOKEN_ACCESS | path userUuid 제거, principal 사용 |
| `/api/v1/users/{userUuid}/popups/**` | `/api/v2/user/popups/**` | TOKEN_ACCESS | path userUuid 제거, principal 사용 |
| `/api/v1/users/{userUuid}/alert/**`의 조회·삭제·읽음 | `/api/v2/user/alert/**` | TOKEN_ACCESS | path userUuid 제거, principal 사용 |
| `/api/v1/favorite/**` | `/api/v2/favorite/**` | TOKEN_ACCESS | caller userUuid 제거, popupUuid 유지 |
| `/api/v1/alert-keyword/**` | `/api/v2/alert-keyword/**` | TOKEN_ACCESS | caller userUuid 제거, keyword 유지 |
| 앱용 `/api/v1/popup/**` 조회 | 같은 suffix의 `/api/v2/popup/**` | TOKEN_ACCESS | 필요한 사용자 식별은 principal 사용 |
| 앱용 `/api/v1/recommend`, `/featured` | 같은 suffix의 `/api/v2/recommend/**` | TOKEN_ACCESS | caller 식별자 없음 |
| `/api/v1/popup-submissions` POST | `/api/v2/popup-submissions` POST | TOKEN_ACCESS | body userUuid 제거, principal 사용 |
| `/api/v1/admin/**` | 같은 suffix의 `/api/v2/admin/**` | TOKEN_ACCESS + ROLE_ADMIN | query caller uuid 제거, principal 사용 |
| `/api/v1/web/popup/**` | 같은 suffix의 `/api/v2/web/popup/**` | permitAll | 사용자 정보 없음 |
| `/api/v1/recommend/web` | `/api/v2/web/recommend` | permitAll | 사용자 정보 없음 |
| CRON·worker endpoint | `/api/v2/internal/**` | SERVICE_WORKER | API Key caller와 target을 분리 |

v1의 `/api/v1/auth/token/test`와 `/api/v1/auth/refresh`는 승인된 실험용 삭제 대상이다. v2
`/api/v2/auth/refresh`는 legacy controller를 복제하지 않고 이 문서의 rotation 계약으로 새로
구현한다. v1 hard-delete·soft-delete·resotre는 유지하지만 v2 계정 상태 변경은 앞서 확정한
`DELETE /api/v2/user` soft-delete 하나만 제공한다.

### principal로 대체할 값

- 사용자 self-service path의 userUuid
- 찜·키워드 body/query의 userUuid
- 개인화 popup path의 userUuid
- 사용자 알림함 path의 userUuid
- popup 제보 body의 submitter userUuid
- 관리자 query의 호출자 uuid

### 그대로 유지할 target 값

- popupUuid
- recommendId
- popupSubmissionId 또는 submissionId
- keyword 값
- worker가 알림을 생성할 recipient userUuid

관리자나 worker가 다른 사용자를 대상으로 작업하는 API에서는 caller와 target을 구분한다.
caller는 JWT 또는 API Key로 인증하고 target UUID는 path/body에 명시적으로 남긴다.

FCM token은 target 식별자가 아니라 인증된 사용자가 등록하는 민감한 device credential이다.
v1의 duplicate-check와 update endpoint는 마이그레이션 동안 그대로 유지한다. v2에는 별도
duplicate-check endpoint를 만들지 않고 `PUT /api/v2/user/fcm-token` 하나만 제공한다. 이 API는
TOKEN_ACCESS를 요구하고 principal 사용자의 token만 request body에서 받아 갱신한다. 저장된 값과
같아도 성공하는 idempotent 계약으로 두며, FCM token을 path·query·응답·로그에 넣지 않는다.

popup 제보의 body userUuid는 제거하지만 principal UUID를 기존 submitter_user_uuid 감사
데이터에 복사한다.

알림 API는 actor에 따라 경로를 분리한다.

- 사용자의 알림함 조회·삭제·읽음 처리는 TOKEN_ACCESS와 principal을 사용한다.
- worker가 특정 사용자에게 알림 이력을 생성하는 API는 /api/v2/internal/**로 옮기고
  SERVICE_WORKER를 요구하며 recipient userUuid를 target으로 유지한다.
- worker polling과 alert 생성 payload의 사용자 식별자는 내부 Long id가 아니라 Users.uuid로
  통일한다.

여기서 Firebase FCM은 푸시 전달 채널이지 PopPang REST API의 인증 주체가 아니다. 현재 저장소와
이력상 실제 주체는 Firebase로 푸시를 보내는 외부 Python 알림 worker로 판단한다. 다만 worker
소스가 이 저장소에 없으므로 구현 직전에 해당 Python 코드 또는 운영 access log로 alert 생성
POST 호출 여부를 확인한다. 반증이 없다면 생성 POST는 worker API로, 나머지는 앱 API로 구현한다.

## v1 호환과 DB 전환

### v1 HTTP 계약

v1은 다음 항목을 characterization test로 고정한다.

- HTTP method와 URL
- path, query, body schema
- 응답 status와 JSON
- request/response media type
- 익명 호출 가능 여부
- Swagger hidden 여부

실험용 token/test와 refresh endpoint 삭제 외에는 v1 mapping을 제거하거나 변경하지 않는다.
v2 JWT filter는 v1 요청에 적용하지 않는다.

기존 JwtAuthenticationFilter가 v1에 선택적으로 principal을 채우던 동작도 JWT 실험 범위로
간주한다. 운영 앱이 사용하던 익명 v1 HTTP 계약은 유지하지만, 실험용 Bearer Token의
인증·오류 동작은 사용자 승인에 따른 명시적 호환 예외로 둔다. 따라서 최우선 원칙의 상태 코드
보존은 Authorization header가 없는 운영 v1 요청에 적용하며, 유효·만료·malformed legacy
Bearer가 붙은 요청은 characterization test로 변화만 기록하고 동일 응답을 보장하지 않는다.

POST /api/v1/auth/autoLogin의 body userUuid 인증 방식은 v2에 복제하지 않는다. 앱 시작 시
Access Token을 사용하고 만료되었으면 refresh하며, 둘 다 없거나 refresh가 실패하면 소셜
로그인으로 이동한다.

### SignupStatus

가입 상태는 nickname null 여부가 아니라 PENDING 또는 COMPLETED enum으로 관리한다.

1. 기존 데이터의 nickname 존재 여부로 예상 분류 건수를 먼저 출력한다.
2. 이상 데이터를 수동 확인한다.
3. 하위 호환 가능한 column을 추가하고 기존 데이터를 backfill한다.
4. v1과 v2의 social login 생성은 PENDING을 기록한다.
5. v1과 v2의 completeSignup은 COMPLETED를 함께 기록한다.
6. 이전 binary rollback 종료를 선언하는 no-return gate를 통과한다.
7. 데이터 확인 후 NOT NULL 제약을 적용한다.

사전 audit는 nickname과 SignupStatus뿐 아니라 null·중복 uuid, null role, null provider, 삭제
상태와 가입 상태의 모순도 포함한다. 발견된 이상 데이터는 자동 추정으로 수정하지 않고 건수와
대상을 별도 승인한다.

### 사용자 식별 제약

- JWT sub로 사용하는 Users.uuid 중복을 사전 점검한 뒤 DB unique를 보장한다.
- 소셜 계정 조회를 findByProviderAndUid로 전환한다.
- 롤백 기간에는 기존 uid 단일 unique를 유지한 채 코드를 먼저 전환한다.
- 롤백 기간과 데이터 상태를 확인한 뒤 uid 단일 unique를 provider+uid 복합 unique로 교체한다.

constraint 교체를 코드 배포와 동시에 수행하지 않는다. 이전 서버로 롤백했을 때
findByUid가 모호해지는 상태를 피하기 위해 단계적으로 진행한다.

과도기에 다른 provider의 동일 uid가 기존 uid unique와 충돌하면 계정을 합치지 않고 명시적
충돌 오류를 반환한다. 모든 web/mobile login과 provider별 signup에서 findByUid 사용이 0건인지
확인한 뒤에만 기존 unique를 제거한다. provider와 uid는 가입 후 변경할 수 없다.

저장소에 DB migration 도구가 없으므로 구현 계획에는 단계별 SQL artifact, index/constraint
이름, audit query, 예상 MySQL lock 영향, backup·복구 절차와 실행 주체를 포함한다. NOT NULL이나
기존 unique 제거 후에는 이전 binary rollback이 불가능하므로 배포 승인 gate를 별도로 둔다.
SQL은 원칙적으로 운영 적용 전에 운영 MySQL과 같은 major version의 격리 환경에서 기존·신규
binary의 읽기·쓰기와 rollback 가능 범위를 검증한다. DB-E1에 한해서는 로컬 MySQL 9.2.0에서
backfill/default 연습을 완료했고, 운영 MySQL 8.0.43과의 버전 차이를 2026-07-19 사용자 결정으로
수용해 동일-major 재연습을 필수 gate에서 제외한다. 이 예외는 운영 DDL/DML 실행 승인이나 이후
DB wave의 동일-major 검증을 생략하는 승인으로 확대하지 않는다.

### Entity/JPA와 DDL 사전 승인

`@Entity`, `@Embeddable`, `@MappedSuperclass`, 영속 field·enum·relation·annotation 또는 native
query의 schema 의존 변경은 구현 전에 멈추고 승인을 받는다. 승인 자료에는 다음을 포함한다.

- 대상 wave, Entity, field/annotation/state method의 현재 값과 변경안
- schema 영향 분류: `NONE`, additive, destructive, no-return
- 대상 DB·table·row 규모와 현재 schema를 확인하는 read-only audit SQL
- 정확한 expand DDL, backfill DML, verify SQL, contract DDL, rollback SQL과 실행 순서
- 구·신규 binary와 구·신규 schema의 호환 범위, v1 영향
- 예상 algorithm/lock/metadata lock, 중단 기준, backup·복원 증거
- 적용 후 test·smoke·관측과 rollback/no-return 지점

실제 schema 변경이 있으면 Entity 코드보다 SQL artifact를 먼저 작성한다. schema delta가 없는
Entity 메서드 변경은 가짜 DDL 대신 `DDL: N/A — schema delta 없음`을 명시하지만, Entity 변경
승인은 동일하게 받는다.

승인은 Entity 코드·SQL 문서 작성, 격리 DB 실행, 운영 read-only audit, 운영 DDL/DML 실행,
`main` merge·운영 배포로 분리한다. 한 단계의 승인이나 일반적인 `진행해줘`를 다음 단계 승인으로
재사용하지 않는다. 에이전트는 별도 실행 승인 없이 외부 DB나 Redis에 접근하지 않는다.

현재 첫 DB wave는 `Users.signup_status`의 NULL backfill과 default 변경이다. 기록된 운영 schema에는
nullable column이 이미 있으므로 ADD COLUMN을 반복하지 않는다. 로컬 복원 DB 연습이 끝났더라도
운영 DB-E1의 승인·적용·검증 전에는 해당 Entity code를 `main`에 merge하지 않는다. v1 provider
signup이 호출하는 `completeSignup(SignupRequestDto)`도 status를 `COMPLETED`로 바꿔야 한다. 이
메서드 변경은 2026-07-19 사용자 승인을 받아 `DDL: N/A — schema delta 없음`으로 구현했으며,
regression test가 프로필 저장과 상태 전이를 함께 고정한다.

DB-E1 로컬 연습은 `poppang_restore_test_20260716`(MySQL 9.2.0)에서 users 303건, NULL 0건,
활성 COMPLETED 182건, 활성 PENDING 1건, 탈퇴 PENDING 120건과 default PENDING을 확인했다. 이
결과는 로컬 migration 연습 완료 증거이며 운영 DB 적용 증거는 아니다.

## Worker 인증과 ETL 전환

worker용 endpoint는 /api/v2/internal/** 아래에 두고 X-Worker-Api-Key header를 요구한다.
API Key는 충분히 긴 random secret을 private config와 ETL secret에 저장하고 constant-time
comparison으로 확인한다.

기존 suffix를 최대한 유지한 첫 v2 worker mapping은 다음과 같다.

| v1 공개 endpoint | v2 worker endpoint |
|---|---|
| `POST /api/v1/popup` | `POST /api/v2/internal/popup` |
| `PUT /api/v1/popup/{popupUuid}/images` | `PUT /api/v2/internal/popup/{popupUuid}/images` |
| `GET /api/v1/user/with-alert-keyword/a` | `GET /api/v2/internal/user/with-alert-keyword/a` |
| `GET /api/v1/user/with-alert-keyword/b` | `GET /api/v2/internal/user/with-alert-keyword/b` |
| `POST /api/v1/users/{userUuid}/alert` | `POST /api/v2/internal/users/{userUuid}/alert` |

v2 polling 응답의 사용자 식별자는 `Users.uuid`로 통일한다. v1 응답은 ETL 전환 전까지 현재
필드와 의미를 유지한다.

API Key를 URL, query parameter, source code, 로그에 넣지 않는다. 사용자 JWT secret과 공유하지
않는다.

전환 순서는 다음과 같다.

1. API Key가 필수인 v2 worker endpoint를 서버에 구현한다.
2. 기존 v1 worker endpoint는 ETL 전환 전까지 공개 상태로 유지한다.
3. v1과 v2 worker 호출량을 기록한다.
4. ETL이 API Key header와 v2 URL을 사용하도록 변경한다.
5. 일정 기간 v1 호출이 0건인지 확인한다.
6. worker v1 endpoint를 다른 v1 API보다 먼저 종료할 수 있다.

v1 공개 기간에는 FCM token과 사용자 정보가 노출되는 legacy 우회 경로가 남는다는 위험을
운영 체크리스트에 명시한다.

## 오류 계약

v2 filter, AuthenticationEntryPoint, AccessDeniedHandler는 모두 기존 ApiResponse JSON 형식을
사용한다.

~~~json
{
  "success": false,
  "code": 5002,
  "message": "만료된 토큰입니다.",
  "data": null
}
~~~

| 상황 | HTTP | ErrorCode |
|---|---:|---|
| 인증 정보 없음 | 401 | 5006 AUTHENTICATION_REQUIRED |
| token 만료 | 401 | 기존 5002 EXPIRED_TOKEN |
| token signature 오류 | 401 | 기존 5005 TOKEN_SIGNATURE_INVALID |
| malformed token | 401 | 기존 5004 MALFORMED_TOKEN |
| 알 수 없는 typ | 401 | 기존 5003 UNSUPPORTED_TOKEN |
| Refresh 불일치·재사용 | 401 | 5007 REFRESH_TOKEN_MISMATCH |
| token 용도 또는 일반 권한 부족 | 403 | 5008 INSUFFICIENT_AUTHORITY |
| MEMBER의 관리자 접근 | 403 | 기존 4203 ACCESS_DENIED |
| Worker API Key 오류 | 401 | 5009 INVALID_WORKER_API_KEY |
| 탈퇴·비활성 계정 | 401 | 5010 ACCOUNT_NOT_ACTIVE |
| Refresh Token body 누락·공백 | 400 | 5011 INVALID_REFRESH_REQUEST |
| Signup provider 경로 불일치 | 403 | 5012 SIGNUP_PROVIDER_MISMATCH |
| Redis 인증 저장소 장애 | 503 | 5013 AUTH_STORE_UNAVAILABLE |
| 최신 Signup Token 불일치 | 401 | 5014 SIGNUP_TOKEN_MISMATCH |
| 가입 완료 사용자 재가입 | 409 | 4204 SIGNUP_ALREADY_COMPLETED |

인증 코드에서 raw RuntimeException을 던지지 않고 BaseException(ErrorCode)으로 정규화한다.
예상하지 못한 내부 오류만 6000 INTERNAL_ERROR로 처리한다.

ACCOUNT_NOT_ACTIVE는 soft-delete 또는 명시적 비활성 계정에만 사용한다. PENDING 사용자는
일반 Access Token 발급 시 권한이 없지만 존재하지 않는 계정처럼 처리하지 않는다. Redis에서
현재 Refresh session을 확인할 수 없는 장애 상황에는 로그인·refresh·logout을 성공으로
간주하지 않고 fail-closed한다. 단, idempotent logout의 정상적인 key 없음과 Redis 연결 장애는
구분한다.

JWT library의 signature, expiration, malformed, unsupported algorithm 예외는 filter 밖으로
전파하지 않고 위 ErrorCode로 명시적으로 변환한다. token 또는 provider credential의 원문을
오류 message와 로그에 포함하지 않는다.

## 테스트 전략

### v1 회귀

- 구현 전 모든 v1 mapping inventory와 OpenAPI snapshot을 만든다.
- v1의 method, path, parameter, status, media type, schema를 고정한다.
- token 실험 endpoint 두 개를 제외한 v1 익명 요청이 계속 성공하는지 검증한다.
- 공통 Service와 Entity를 변경할 때 대응하는 v1 Controller·Service 회귀 테스트를 추가한다.

### JWT와 Security

- Access, Refresh, Signup의 claim, audience, typ, jti, TTL을 고정 Clock으로 검증한다.
- 만료, signature, issuer, audience, malformed, unsupported typ을 각각 검증한다.
- HS256 이외의 algorithm과 none을 거절하고, 검증되지 않은 typ으로 parser를 선택하지 않는지
  검증한다.
- v2 web, login, refresh는 익명 접근 가능해야 한다.
- 일반 v2는 TOKEN_ACCESS, signup은 TOKEN_SIGNUP을 요구해야 한다.
- admin은 TOKEN_ACCESS와 ROLE_ADMIN을 모두 요구해야 한다.
- 삭제·비활성·PENDING 사용자가 Access 인증을 얻지 못해야 한다.
- 모든 401/403이 ApiResponse JSON 계약을 지켜야 한다.
- v2 JWT filter와 worker filter가 Servlet global filter로 자동 등록되지 않고 각 chain에만
  적용되는지 검증한다.
- /api/v2/web/**에 write endpoint 또는 사용자·관리자용 response가 추가되면 architecture test가
  실패해야 한다.
- 공개 추천은 `/api/v2/web/recommend`에서만 200이고 `/api/v2/recommend/web` mapping은 존재하지
  않는지 검증한다.

### Refresh와 Redis

- 로그인 시 hash와 TTL이 저장되는지 검증한다.
- 새 로그인으로 이전 Refresh Token이 무효화되는지 검증한다.
- rotation 성공 시 Access/Refresh가 모두 교체되는지 검증한다.
- 같은 Refresh Token의 동시 요청 중 하나만 성공하는지 검증한다.
- 이전 token 재사용, Redis mismatch, 만료를 거절하는지 검증한다.
- logout과 회원 탈퇴 시 Refresh key가 삭제되는지 검증한다.
- 이전 sid의 Access Token으로 logout해도 새 로그인 session이 삭제되지 않는지 검증한다.
- refresh와 logout, refresh와 회원 탈퇴가 동시에 실행될 때 삭제된 key가 다시 생성되지 않는지
  검증한다.
- Redis 연결 장애를 정상적인 token mismatch나 logout key 없음으로 오인하지 않는지 검증한다.
- atomic compare-and-replace는 실제 Redis 통합 테스트에서도 동시에 요청해 정확히 하나만
  성공하는지 확인한다.

### 회원 탈퇴

- v1 hard-delete, soft-delete, resotre의 method, path, 익명 호출 계약이 유지되는지 검증한다.
- v2 DELETE /api/v2/user가 TOKEN_ACCESS 없이는 401인지 검증한다.
- request가 userUuid를 받지 않고 principal 사용자만 soft-delete하는지 검증한다.
- Users row와 연관 데이터는 남고 `is_deleted=true`만 적용되는지 검증한다.
- 탈퇴 직후 기존 Access Token, Refresh Token, social login이 모두 거절되는지 검증한다.
- Refresh/Signup Redis key 삭제 성공과 실패를 각각 검증한다.
- Redis key 삭제 실패에도 DB 탈퇴 상태 때문에 token 인증이 실패하는지 검증한다.
- v2에 앱용 hard-delete mapping이 존재하지 않는지 architecture test로 검증한다.

### 인증 use case

- COMPLETED, 신규, PENDING, 삭제 사용자별 소셜 로그인 응답을 검증한다.
- Signup Token으로만 회원가입할 수 있는지 검증한다.
- Signup Token의 허용 경로가 정확히 세 provider별 회원가입 POST뿐인지 architecture/security
  test로 검증하고, 닉네임 중복·추천 조회 등 다른 v2 API 호출은 403인지 확인한다.
- 회원가입 POST 자체가 닉네임 중복과 추천 ID 유효성을 검증해 잘못된 입력을 4xx로 거절하는지
  검증한다.
- body의 uid, userUuid, provider, role로 다른 사용자를 선택할 수 없는지 검증한다.
- 같은 Signup Token의 동시 회원가입 요청 중 하나만 성공하고, 첫 요청 commit 시 나머지는
  409이며 consume 후 rollback 시에는 401인지 검증한다.
- PENDING 재로그인으로 새 Signup Token을 발급하면 이전 token이 즉시 401로 거절되는지 검증한다.
- Signup Token 최신 hash의 비교·삭제가 원자적으로 한 번만 성공하는지 실제 Redis에서 검증한다.
- PENDING login과 signup, 동일 provider+uid 동시 login, Redis 응답 timeout, SET 후 DB commit 실패,
  compare-delete 후 DB rollback·process 중단을 검증한다.
- v2 self-service가 principal을 사용하고 target 식별자는 보존하는지 검증한다.
- v2 FCM 갱신이 body 값과 principal만 사용하고 같은 token의 반복 요청도 성공하는지 검증한다.
- v2에 FCM duplicate-check mapping이 없고 FCM token이 path·query·응답에 노출되지 않는지
  architecture/controller test로 검증한다.
- MEMBER와 ADMIN 권한 경계를 검증한다.
- Worker API Key 성공, 누락, 불일치를 검증한다.

### 최종 명령

~~~bash
./gradlew spotlessCheck
./gradlew clean build
~~~

v1 회귀와 v2 보안 test가 모두 통과해야 구현 완료로 판단한다.
실제 Redis 통합 테스트에는 Testcontainers 또는 CI가 제공하는 격리 Redis를 사용한다. 순수 mock
테스트만으로 Lua script, TTL, 경쟁 상태를 검증했다고 간주하지 않는다.

## OpenAPI

- v1과 v2를 별도 SpringDoc group으로 제공한다.
- v1 문서는 기존 snapshot과 전역 Bearer 표기를 포함해 마이그레이션 중에는 그대로 유지한다.
  실제 v1 익명 허용과 문서 표기가 다른 문제를 고치는 작업은 별도 호환 변경으로 다룬다.
- v2 public operation은 security requirement를 제거한다.
- v2 Access·Signup operation은 Bearer scheme과 필요한 token 용도를 설명한다.
- worker operation은 별도 API Key security scheme을 사용한다.
- hidden은 문서 노출 여부일 뿐 인가 수단으로 사용하지 않는다.

## 배포와 전환

정확한 배포 단위는 구현 체크리스트의
[Merge and production deployment wave map](../plans/2026-07-15-jwt-v2-migration.md#merge-and-production-deployment-wave-map)을
따른다. 현재 CI/CD에서 `main` push는 운영 배포이므로 각 wave는 별도 branch/PR/merge, smoke와
rollback 단위다.

모든 진행 보고는 `현재 배포 청크 n/16`, 구현·검증 상태, `운영 배포 완료 m/16`과 배포 판단을
함께 표시한다. gate가 남으면 `지금 배포하면 안 됩니다`, 모든 merge 전 조건이 충족되면
`지금 배포할 단계입니다 — 별도 승인 필요`라고 명시한다. 운영 배포 완료 숫자는 main merge,
production deploy, health와 smoke가 모두 끝난 뒤에만 증가시킨다.

1. 1/16에서 v1 inventory·익명 호환 기준선만 먼저 배포한다.
2. DB 중복과 SignupStatus 분류를 점검하고 DB-E1의 backup·DDL·lock·rollback을 별도 승인받아
   수동 적용한다. DB 작업을 CD에 포함하지 않는다.
3. v1 signup 상태 전이와 구·신규 binary 호환을 검증한 뒤 2/16 Users 기반을 배포한다.
4. private config를 먼저 준비하고 3/16 JWT·Redis 기반과 4/16 Security 경계를 각각 독립 배포한다.
   3/16이 legacy `JwtProvider`에 영향을 주면 v1 동작을 보존하는 additive 변경이어야 하며, 그렇지
   않으면 feature-disabled v2 provider로 분리하고 배포 단위를 다시 승인받는다.
5. 5/16~15/16의 auth·사용자·popup·Web·Admin·worker API는 체크리스트처럼 provider·도메인
   경계로 나눠 배포한다. API wave마다 OpenAPI 보안 표기와 저카디널리티 관측도 함께 제공한다.
6. 각 wave에서 main CD가 test와 formatting을 통과한 동일 JAR로 image를 만들고 digest를 기록한다.
   Actuator, 대표 v1 익명 API, 해당 v2의 401/403/성공 smoke와 5xx/DB·Redis 오류 부재를 확인한
   뒤에만 다음 wave를 merge한다.
7. 16/16에서 전체 v1/v2 회귀, OpenAPI·관측 누락과 migration matrix를 최종 검증한다.
8. 필요한 서버 wave가 모두 안정화된 뒤 iOS와 Android를 v2로 전환한다.
9. ETL은 별도 일정과 승인으로 v2 worker API에 전환한다.
10. 구버전 앱 지원 종료와 route별 v1 호출 0건을 확인한 뒤 contract SQL을 no-return 승인으로
    적용한다.
11. v1 삭제는 별도 change와 별도 배포로 수행한다.

DB 변경은 이전 서버가 모르는 column을 무시할 수 있는 추가 방식으로 시작한다. rollback 기간에
column 삭제, 이름 변경, 기존 값 의미 변경을 수행하지 않는다.

현재 GitHub Actions의 PR build와 main 배포 workflow가 같은 test gate를 실제로 실행하는지 구현
단계에서 함께 점검한다. 배포 workflow가 bootJar만 수행한다면 배포 전 test gate를 추가하고,
별도의 재빌드 없이 검증된 JAR로 image를 만든다.

rollback 가능 범위는 전환 단계에 따라 다르다.

- 모바일 전환 전: no-return DB gate 전이면 pre-v2 binary까지 rollback할 수 있다.
- 모바일 전환 후: `/api/v2/**` 계약을 지원하는 직전 v2 호환 image까지만 rollback할 수 있다.
- no-return DB gate 후: 해당 schema를 지원하지 않는 binary로 rollback하지 않고 forward-fix 또는
  문서화한 DB 복구 절차를 사용한다.

배포 후 smoke test가 실패하면 신규 v2 traffic을 중단하고 현재 단계에서 허용된 image digest로만
rollback한다.

## 관측

다음 항목을 route template과 API version 기준으로 집계한다.

- v1/v2 호출량
- 401, 403, 409, 5xx
- 인증 ErrorCode별 발생량
- REFRESH_TOKEN_MISMATCH
- 로그인 성공, Signup 필요, 실패 비율
- iOS/AOS version별 v2 전환율
- worker v1/v2 호출량

UUID, token, token hash, email, FCM token, API Key를 metric label이나 로그에 넣지 않는다.

모바일은 v2 요청에 서버가 허용 목록으로 검증할 수 있는 X-App-Platform과 X-App-Version을
보낸다. 값은 정규화된 저카디널리티 label로만 사용하고 임의 문자열을 그대로 metric label로
사용하지 않는다. v1은 이 header가 없는 구버전이 있으므로 route별 전체 호출량을 삭제 판단의
기준으로 삼는다.

인증 관련 구조화 로그에는 request id, route template, API version, platform/version, HTTP status,
ErrorCode만 기록한다. endpoint별 v1 호출 0건은 최소 30일 연속 관찰하고, 그 기간 중 지원 대상
최소 앱 version과 강제 업데이트 정책도 함께 기록한다. 일시적인 metric 누락을 0건으로 오인하지
않도록 수집 pipeline 정상 여부를 삭제 gate에 포함한다.

## 마이그레이션·삭제 매트릭스

구현을 시작하기 전에 모든 v1 mapping을 행 단위로 조사해 v2 경로·actor·target·인증·DTO
변경·삭제/대체 사유를 먼저 채운다. 이 baseline matrix가 승인되기 전에는 v2 Controller 구현을
시작하지 않는다. 구현 후에는 BE test, iOS, AOS, ETL, 호출량, 삭제 가능 열을 갱신한다.

| 도메인 | v1 method/path | 처리 | v2 method/path | actor | target | 인증 | DTO 변경 | BE test | iOS | AOS | ETL | 최근 v1 호출 | 삭제 가능 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 예시: 찜 등록 | POST /api/v1/favorite | v2 twin | POST /api/v2/favorite | 로그인 사용자 | popupUuid | TOKEN_ACCESS | body userUuid 제거 | 완료 | 버전 기록 | 버전 기록 | 해당 없음 | 시각 기록 | 아니오 |

iOS/AOS 열에는 코드 merge 여부뿐 아니라 실제 배포 version과 구버전 지원 종료 여부를 기록한다.
ETL이 호출하지 않는 API는 해당 없음으로 표시한다.

baseline matrix는 public 조회, 개인화, self-service, admin, social auth, hidden, worker/internal을
모두 포함한다. UUID 입력이 없는 관리자 상태 변경, popup 등록·이미지 upsert도 빠뜨리지 않는다.
각 행의 처리는 v2 twin, 다른 v2 흐름으로 대체, v1 전용 유지, 삭제 중 하나여야 한다.

개별 v1 endpoint는 다음 조건을 모두 만족할 때만 삭제 가능이다.

- 대응 v2 구현과 BE test 완료
- iOS 전환 완료
- AOS 전환 완료
- 해당되는 경우 ETL 전환 완료
- 구버전 앱 지원 종료
- server rollback 관찰 기간 종료
- 최소 30일 연속 route 호출 0건과 관측 pipeline 정상 확인

## 완료 조건

- v1 실험용 token endpoint 두 개를 제외한 현재 API 계약이 유지된다.
- v2 공개 경로 외 모든 앱 API가 TOKEN_ACCESS를 요구한다.
- Signup Token은 회원가입에만 사용할 수 있다.
- Signup Token은 사용자별 최신 하나만 유효하고 가입 시 원자적으로 소비된다.
- admin과 worker 인증이 사용자 API와 분리된다.
- 소셜 로그인, signup, refresh, logout이 확정된 token 계약을 따른다.
- Refresh rotation이 원자적으로 동작하고 이전 token이 즉시 무효화된다.
- 탈퇴·비활성 사용자가 token을 발급·refresh·인증할 수 없다.
- v2 본인 탈퇴는 JWT principal 기반 soft-delete이고 앱용 hard-delete가 없다.
- v2에서 호출자 userUuid를 request 값으로 신뢰하지 않는다.
- v1 회귀, v2 Security, Redis concurrency test가 통과한다.
- endpoint별 마이그레이션·삭제 매트릭스를 제공한다.
- v2 구현 완료와 공개 v1 우회 경로까지 닫힌 인증 마이그레이션 완료를 구분해 보고한다.

## 첫 버전 범위 밖

- 다중 기기 Refresh Token
- Refresh Token rotation 유예 시간
- Refresh Token 재사용 감지에 따른 session family 전체 폐기
- 활동 중 session의 절대 최대 수명
- Access Token blacklist와 즉시 폐기
- Users.authVersion 기반 token 세대 관리
- mTLS 또는 Service JWT 기반 worker 인증
- 탈퇴 데이터의 보존 기간 이후 물리 삭제·익명화 작업
- 탈퇴 사용자 self-restore
- v1 endpoint 실제 삭제
- 모바일·ETL 저장소의 코드 변경
