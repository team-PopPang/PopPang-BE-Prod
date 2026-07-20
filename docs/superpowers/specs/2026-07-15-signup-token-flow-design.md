# v2 회원가입 및 Signup Token 흐름 설계

## 목적

소셜 로그인에서 검증한 사용자와 PopPang 회원가입을 완료할 사용자를 안전하게 연결한다.
신규 또는 가입 미완료 사용자는 일반 앱 API를 호출할 수 없고, 짧게 유효한 Signup Token으로
회원가입 API만 호출할 수 있어야 한다.

이 문서는 전체 JWT 마이그레이션 중 회원가입과 Signup Token 흐름만 다룬다. 기존 v1 API는
클라이언트 마이그레이션 동안 유지하되, v2 전환 확인 후 제거한다.

## 현재 동작과 문제

현재 Kakao, Google, Apple 로그인 서비스는 소셜 제공자의 credential을 검증한 뒤 `uid`로
`Users`를 조회하거나 미완성 사용자를 생성하고 `LoginResponseDto`를 반환한다. PopPang
Access Token과 Refresh Token은 소셜 로그인 응답에 연결되어 있지 않다.

현재 provider별 회원가입 API는 `SignupRequestDto` body에서 다음 신원 관련 값을 받는다.

- `uid`
- `provider`
- `email`
- `role`

서비스는 body의 `uid`로 사용자를 다시 조회한다. 따라서 소셜 로그인에서 검증한 사용자와
후속 회원가입 요청을 서버가 증명 가능한 방식으로 연결하지 않는다. `uid`는 비밀번호가 아니며,
클라이언트가 보낸 값을 신원 증명으로 신뢰해서는 안 된다.

또한 hidden `POST /api/v1/auth/token/test`는 임의의 `userUuid`로 토큰을 발급할 수 있다.
`@Hidden`은 OpenAPI 노출만 막을 뿐 보안 기능이 아니므로, JWT 기반 v2를 운영하기 전에 이
endpoint와 함께 실험용 `POST /api/v1/auth/refresh`를 삭제한다.

## 핵심 결정

### 발급 시점

Signup Token은 회원가입 화면 진입 시점에 발급하지 않는다. 서버가 Kakao, Google, Apple의
credential을 검증하고 내부 사용자를 식별한 직후 발급한다.

클라이언트의 화면 이동은 신원 증명이 아니다. 앱은 소셜 로그인 API 응답의
`signupStatus` 값을 보고 회원가입 화면으로 이동한다.

### 사용자 상태별 응답

소셜 로그인 검증 후 서버는 사용자의 가입 상태를 기준으로 응답한다.

| 사용자 상태 | 응답 | 허용되는 다음 동작 |
|---|---|---|
| 가입 완료 `COMPLETED` | Access Token + Refresh Token | 일반 v2 앱 API 호출 |
| 신규 사용자 | 가입 대기 사용자 생성 + Signup Token | 회원가입 POST 호출 |
| 기존 가입 대기 `PENDING` | 기존 사용자에 대한 새 Signup Token | 중단했던 회원가입 재개 |
| 탈퇴 사용자 | `ACCOUNT_NOT_ACTIVE` 오류 | 자동 재가입 또는 token 발급 금지 |

Signup Token은 신규 계정을 만들 때마다 발급하는 식별자가 아니다. 동일한 소셜 사용자가 가입을
중단한 뒤 다시 로그인하면 기존 `PENDING` 사용자를 조회하고 새 Signup Token을 발급한다.

### 가입 상태 모델

`Users`에 가입 상태를 명시적으로 저장한다.

```java
public enum SignupStatus {
  PENDING,
  COMPLETED
}
```

`nickname == null` 같은 간접 조건으로 가입 완료 여부를 영구 판단하지 않는다. 기존 데이터
마이그레이션에서는 `nickname`이 존재하는 사용자를 `COMPLETED`, 없는 사용자를 `PENDING`으로
분류하되, 적용 전에 각 분류 건수를 출력해 데이터 상태를 확인한다.

### 소셜 계정 식별자

소셜 사용자는 전역 `uid` 하나가 아니라 `(provider, uid)` 조합으로 식별한다. 현재
`UsersRepository#findByUid`와 `users.uid` 단일 unique 제약은 provider가 다른 동일 문자열을 같은
사용자로 오인할 수 있으므로, v2 인증 도입 시 provider와 uid의 복합 고유 제약과
`findByProviderAndUid` 조회로 전환한다.

JWT `sub`로 사용하는 `Users.uuid`에도 DB unique 제약을 보장한다. 기존 데이터에서 provider/uid
또는 uuid 중복이 발견되면 임의로 병합하지 않고 배포 전에 별도 데이터 정리 대상으로 보고한다.

## 전체 흐름

```text
사용자가 소셜 로그인 선택
  → 앱이 provider credential을 획득
  → v2 소셜 로그인 API 호출
  → 서버가 provider credential 검증
  → (provider, uid)에 해당하는 내부 사용자 조회
      ├─ COMPLETED
      │   → Access Token + Refresh Token 발급
      │   → signupStatus=COMPLETED
      │
      └─ 신규 또는 PENDING
          → 내부 사용자를 PENDING 상태로 생성 또는 재사용
          → Signup Token 발급
          → signupStatus=PENDING
          → 앱이 회원가입 화면으로 이동
          → Signup Token으로 provider별 회원가입 POST 호출
          → 서버가 가입 정보 검증 및 저장
          → SignupStatus를 COMPLETED로 변경
          → Access Token + Refresh Token 발급
```

회원가입 화면을 직접 열었지만 Signup Token이 없다면 회원가입 POST는 HTTP 401로 거절한다.

## HTTP 계약

### 소셜 로그인

v2에서도 기존 provider별 로그인 경로 구조를 유지한다.

```http
POST /api/v2/auth/kakao/mobile/login
POST /api/v2/auth/google/mobile/login
POST /api/v2/auth/apple/mobile/login
```

이 endpoint들은 소셜 credential 자체를 검증해야 하므로 `permitAll`이다. `permitAll`은 요청을
신뢰한다는 뜻이 아니라, PopPang Access Token 없이 provider credential 검증을 시작할 수 있다는
뜻이다.

신규 또는 가입 미완료 사용자 응답 예시는 다음과 같다.

```json
{
  "signupStatus": "PENDING",
  "tokenType": "Bearer",
  "signupToken": "eyJ...",
  "signupTokenExpiresIn": 900
}
```

가입 완료 사용자 응답 예시는 다음과 같다.

```json
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
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 2592000
}
```

한 응답에서 Signup Token과 Access/Refresh Token을 함께 반환하지 않는다.
실제 응답은 기존 `ApiResponse`의 `data` 안에 담고, `expiresIn`은 초 단위다. `uid`와 FCM
token은 로그인 응답에 포함하지 않는다. token을 반환하는 응답에는 `Cache-Control: no-store`와
`Pragma: no-cache`를 설정한다.

### 회원가입

기존 URL 호환 방침에 맞춰 v2에서도 provider별 회원가입 endpoint를 유지한다.

```http
POST /api/v2/auth/kakao/signup
Authorization: Bearer <SIGNUP_TOKEN>

POST /api/v2/auth/google/signup
Authorization: Bearer <SIGNUP_TOKEN>

POST /api/v2/auth/apple/signup
Authorization: Bearer <SIGNUP_TOKEN>
```

요청한 provider 경로와 Signup Token의 `sub`로 찾은 `Users.provider`가 다르면 요청을 거절한다.

v2 회원가입 request body에는 서버가 이미 검증한 신원 정보를 포함하지 않는다.

```json
{
  "nickname": "팝팡사용자",
  "isAlerted": true,
  "fcmToken": "device-fcm-token",
  "alertKeywordList": ["성수", "캐릭터"],
  "recommendList": [1, 3]
}
```

다음 값은 body에서 제거하고 서버가 pending 사용자와 Signup Token에서 결정한다.

- `uid`
- `userUuid`
- `provider`
- `role`
- 소셜 제공자가 확인한 `email`

회원가입 완료 전에 앱이 닉네임 중복 확인이나 추천 목록 조회 API를 따로 호출하지 않는 현재
흐름을 유지한다. 따라서 Signup Token의 허용 목록은 위 세 provider별 회원가입 POST로만
한정한다. 닉네임 중복과 `recommendList`의 유효성은 회원가입 POST 안에서 검증하고, 중복
닉네임이나 잘못된 추천 ID가 있으면 해당 POST가 명시적인 4xx 오류를 반환한다. 닉네임·추천
관련 일반 v2 endpoint를 Signup Token용으로 추가하거나 `permitAll`로 열지 않는다.

회원가입 성공 응답은 사용자 프로필과 정식 Access/Refresh Token을 반환한다. 성공 후 앱은
Signup Token을 삭제하고 정식 토큰만 안전한 기기 저장소에 보관한다.

## Signup Token 계약

Signup Token은 기존 `JwtProvider`를 확장해 발급하고 검증한다. 별도 토큰 필터를 추가하지 않는다.

| claim | 값 | 목적 |
|---|---|---|
| `sub` | 내부 `Users.uuid` | 가입 대기 사용자 식별 |
| `iss` | 기존 PopPang issuer | 발급자 검증 |
| `aud` | `poppang-signup-v2` | 일반 API용 토큰과 용도 분리 |
| `iat` | 발급 시각 | 토큰 생성 시각 기록 |
| `exp` | 발급 시각 + 15분 | 회원가입 입력 시간과 공격 가능 시간의 균형 |
| `jti` | 매 발급마다 새 UUID | 같은 시각에 발급해도 서로 다른 토큰 보장 |
| `typ` | `SIGNUP` | Signup Token 명시 |

기본 유효기간은 15분이며 private config의 `jwt.signup-token-exp-minutes`로 관리한다. Signup
Token에는 사용자 role, email, 닉네임, FCM token 같은 변경 가능한 개인정보를 넣지 않는다.

Signup Token에는 Refresh Token을 발급하지 않는다. 만료되면 클라이언트가 소셜 로그인을 다시
수행하고 새 Signup Token을 받는다.

## 인증 필터와 인가 정책

하나의 `JwtAuthenticationFilter`가 Bearer Token을 한 번 파싱하고 token type에 따라 다른
authority를 만든다.

| token type | 생성할 authority | 용도 |
|---|---|---|
| `ACCESS` | `TOKEN_ACCESS` + `ROLE_MEMBER` 또는 `ROLE_ADMIN` | 일반 앱/관리자 API |
| `SIGNUP` | `TOKEN_SIGNUP`만 | 회원가입 POST |
| `REFRESH` | SecurityContext 인증을 만들지 않음 | refresh 서비스에서만 검증 |

Signup Token도 서명 검증을 통과한 인증 정보이므로 단순 `authenticated()` 조건을 만족할 수 있다.
따라서 일반 v2 앱 API의 기본 정책은 `authenticated()`가 아니라 `TOKEN_ACCESS` authority를
요구해야 한다.

정책 적용 순서는 다음과 같다.

```text
공개 Web API                      → permitAll
정확한 소셜 로그인·refresh 경로   → permitAll
위 세 provider별 v2 회원가입 POST → TOKEN_SIGNUP 필요
/api/v2/admin/**                  → TOKEN_ACCESS + ROLE_ADMIN 필요
그 밖의 /api/v2/**                → TOKEN_ACCESS 필요
미등록 경로                        → denyAll
```

회원가입 화면을 돕기 위한 별도 endpoint는 이 허용 목록에 넣지 않는다. Signup Token으로
닉네임 중복 확인, 추천 목록 조회 등 다른 v2 API를 호출하면 403을 반환한다.

회원가입 서비스에도 `@PreAuthorize("hasAuthority('TOKEN_SIGNUP')")`를 적용하면 HTTP 경로 외의
호출에서도 같은 제한을 유지할 수 있다.

## 서버 저장과 Redis 정책

Signup Token은 사용자별로 가장 최근에 발급한 하나만 유효하다. Redis에는 원문이 아니라
SHA-256 hash를 저장한다.

```text
key   = auth:v2:signup:{userUuid}
value = { signupTokenHash, jti, issuedAt }
TTL   = JWT exp까지 남은 시간, 최대 15분
```

신규 또는 `PENDING` 사용자가 소셜 로그인을 다시 하면 같은 key를 새 token 정보로 덮어쓴다.
따라서 이전 Signup Token은 JWT 자체의 만료 시각이 남아 있어도 회원가입에 사용할 수 없다.
Redis key가 없거나 hash가 다르면 `SIGNUP_TOKEN_MISMATCH`로 거절한다.

최신 key는 hash 저장과 만료 설정을 `SET ... EXAT/PXAT` 또는 동등한 하나의 원자 명령으로
기록한다. `SET`과 `EXPIRE`를 나누지 않아 중간 장애로 만료 없는 key가 남지 않게 한다.

회원가입 성공 시 DB 상태를 `COMPLETED`로 바꾸므로 같은 Signup Token을 다시 제출해도 가입을
반복할 수 없다. Redis의 hash 비교와 key 삭제는 Lua script 또는 동등한 원자 연산으로 처리해
동일 token을 두 요청이 동시에 소비하지 못하게 한다.

Signup Token 발급과 회원가입 완료는 같은 사용자 row의 쓰기 잠금을 사용한다. 발급 쪽이 먼저
잠금을 잡으면 새 hash를 저장한 뒤 잠금을 해제하므로 이전 token 요청은 mismatch가 된다.
회원가입 쪽이 먼저 잠금을 잡으면 token을 원자적으로 소비하고 `COMPLETED`로 바꾸므로 뒤늦은
로그인은 정식 Access/Refresh Token 흐름으로 처리된다.

모든 경합 경로의 순서는 `Users row write lock → 상태 재검증 → Redis`로 통일한다. 발급 writer도
잠금 안에서 PENDING이고 삭제·비활성이 아닌지 다시 확인한다. 잠금 뒤 COMPLETED이면 Signup
Token을 만들지 않고 정식 로그인 결과로 분기한다. 동일 provider+uid 신규 로그인 unique 충돌도
기존 row를 다시 조회한 다음 같은 잠금 순서를 따른다.

JWT filter는 검증에 사용한 정확한 compact JWT 문자열의 SHA-256 fingerprint를 즉시 계산한다.
raw token 대신 fingerprint와 검증된 jti만 transient Authentication details에 담아 서비스로
전달한다. Lua script는 인증된 principal의 sub로 key를 만들고 저장된 hash와 jti가 모두 일치할
때만 삭제한다. 서비스가 Authorization header를 다시 파싱하거나 request의 userUuid로 key를
선택하지 않는다. fingerprint와 jti도 로그에 남기지 않는다.

Redis는 이 흐름의 인증 저장소이므로 연결 장애를 hash 불일치로 취급하지 않는다. 발급과
회원가입을 모두 `AUTH_STORE_UNAVAILABLE` 503으로 fail-closed하고, 사용자는 장애 복구 후 소셜
로그인부터 다시 시도한다. 사용자 삭제·비활성 처리 시 Signup key도 함께 삭제한다. Redis SET
또는 compare-delete가 timeout되면 명령 실행 여부를 알 수 없으므로 자동 재시도하지 않는다.
DB transaction을 rollback하고 503을 반환하며, token이 이미 교체·소비되었을 가능성을 전제로
소셜 로그인부터 새로 시작한다.

Refresh Token은 `auth:v2:refresh:{userUuid}`에 원문이 아닌 SHA-256 hash로 저장한다. Signup
Token과 저장 공간 및 수명주기를 공유하지 않는다.

## 회원가입 처리 순서

provider별 v2 회원가입 서비스는 다음 순서를 지킨다.

1. JWT 필터가 서명, issuer, audience, 만료, `typ=SIGNUP`을 검증한다.
2. 필터가 `sub`를 principal의 `userUuid`로 설정하고 `TOKEN_SIGNUP` authority를 부여한다.
3. 회원가입 transaction을 시작하고 principal의 `userUuid`에 해당하는 사용자 row를 쓰기
   잠금으로 조회한다.
4. 사용자가 존재하고 삭제되지 않았으며 `SignupStatus=PENDING`인지 잠금 안에서 다시 확인한다.
5. 사용자 provider와 요청 URL의 provider가 같은지 확인한다.
6. 닉네임 중복, keyword/recommend 목록, FCM token 등 request 값을 검증한다.
7. Redis에서 제출 token hash가 최신 hash와 같은지 비교하고 같은 원자 연산에서 key를 삭제한다.
8. 하나의 DB 트랜잭션에서 사용자 프로필과 연관 데이터를 저장하고 상태를 `COMPLETED`로 바꾼다.
9. DB 트랜잭션 commit이 끝난 뒤 정식 Access Token과 Refresh Token을 발급한다.
10. Refresh Token hash를 Redis에 저장한다.
11. 성공 응답을 반환한다.

다음 원칙은 회원가입 완료 후 발급하는 Access/Refresh Token에 적용한다. 가입 완료 DB
트랜잭션과 Refresh Redis 저장을 하나의 트랜잭션처럼 간주하지 않고, DB를 먼저 commit한 뒤
정식 token을 발급·저장한다. Refresh Redis 저장 또는 응답 전송이 실패하면 사용자는 소셜
로그인을 다시 수행한다. 이미 `COMPLETED`이므로 서버는 정상 로그인 흐름으로 Access/Refresh
Token을 다시 발급한다. 실패한 회원가입 POST를 Signup Token으로 반복해 프로필을 덮어쓰지는
않는다.

반대로 DB commit 전에 Refresh Token을 Redis에 저장하지 않는다. DB가 rollback되었는데
Refresh Token만 남아 가입 미완료 사용자가 토큰을 갱신하는 상태를 방지하기 위해서다. refresh
서비스도 사용자가 삭제되지 않았고 `COMPLETED`인지 다시 확인한다.

Signup Token을 원자적으로 소비한 뒤 DB transaction이 rollback되면 해당 token을 복구하지
않는다. 사용자는 소셜 로그인을 다시 수행해 새 Signup Token을 받아야 한다. 반대로 token 발급
transaction에서는 사용자 row를 잠근 상태로 `PENDING` 사용자를 저장·flush하고 Signup Token을
생성한 뒤 Redis 최신 hash를 기록한다. Spring transaction proxy가 commit을 완료한 다음에만
orchestrator가 token을 HTTP 응답으로 반환한다. Redis 기록 후 DB commit이 실패하면 반환되지
않은 hash가 최대 15분 남을 수 있지만 어떤 client도 원문 token을 받지 않았고, 다음 로그인에서
덮어쓴다.

같은 token의 동시 요청에서 첫 요청이 DB commit까지 성공하면 뒤 요청은 COMPLETED를 확인해
`SIGNUP_ALREADY_COMPLETED` 409를 받는다. 첫 요청이 Redis consume 후 rollback 또는 중단되면
뒤 요청은 key 불일치로 `SIGNUP_TOKEN_MISMATCH` 401을 받는다.

## 오류 처리

모든 인증·인가 오류는 `AuthenticationEntryPoint`와 `AccessDeniedHandler`를 통해 기존
`ApiResponse` JSON 형식으로 반환한다.

| 상황 | HTTP | 처리 |
|---|---:|---|
| Signup Token 없음 | 401 | `AUTHENTICATION_REQUIRED` |
| 서명·issuer·audience가 잘못됨 | 401 | `INVALID_TOKEN` 계열 오류 |
| Signup Token 만료 | 401 | 기존 `EXPIRED_TOKEN` 오류 |
| Access Token으로 signup 호출 | 403 | `INSUFFICIENT_AUTHORITY` |
| Refresh Token을 Bearer Token으로 사용 | 401 | `UNSUPPORTED_TOKEN` |
| Signup Token으로 일반 앱 API 호출 | 403 | `INSUFFICIENT_AUTHORITY` |
| 사용자 없음 또는 삭제됨 | 401 | `ACCOUNT_NOT_ACTIVE` |
| 최신 Signup Token이 아님 | 401 | `SIGNUP_TOKEN_MISMATCH` |
| 이미 가입 완료 | 409 | `SIGNUP_ALREADY_COMPLETED` |
| provider 경로 불일치 | 403 | `SIGNUP_PROVIDER_MISMATCH` |
| 닉네임 중복 | 409 | 기존 `DUPLICATE_NICKNAME` |
| Redis 인증 저장소 장애 | 503 | `AUTH_STORE_UNAVAILABLE` |

새 오류는 `ErrorCode`의 Auth/JWT 또는 Users 대역에 추가하고 raw `RuntimeException`을 사용하지
않는다.

## 클라이언트 처리

- 회원가입 화면은 화면 진입 이벤트로 토큰을 요청하지 않는다.
- 소셜 로그인 응답이 `signupStatus=PENDING`일 때만 회원가입 화면으로 이동한다.
- Signup Token은 로그나 분석 이벤트에 기록하지 않는다.
- 회원가입 동안 메모리 또는 Keychain/Keystore 같은 안전한 기기 저장소에 임시 보관한다.
- 회원가입 성공 또는 사용자가 흐름을 취소하면 로컬 Signup Token을 삭제한다.
- 재로그인으로 새 Signup Token을 받으면 기존 로컬 Signup Token을 즉시 교체한다.
- 소셜 로그인 요청도 single-flight로 처리해 같은 계정의 로그인 요청을 동시에 보내지 않는다.
- 토큰 만료 시 소셜 로그인부터 다시 수행한다.
- Signup Token을 Refresh Token처럼 재발급하거나 자동 연장하지 않는다.

## 테스트 전략

### 토큰 단위 테스트

- Signup Token의 `sub`, `iss`, `aud`, `iat`, `exp`, `jti`, `typ`을 검증한다.
- 15분 만료 정책을 고정된 Clock으로 검증한다.
- 만료, 잘못된 서명, issuer, audience, token type을 각각 거절한다.
- Signup Token을 Access Token 또는 Refresh Token으로 해석하지 않는다.

### 소셜 로그인 서비스 테스트

- `COMPLETED` 사용자는 Access/Refresh Token만 받는다.
- 신규 사용자는 `PENDING`으로 생성되고 Signup Token만 받는다.
- 기존 `PENDING` 사용자는 중복 생성되지 않고 새 Signup Token을 받는다.
- `PENDING` 재로그인에서 새 token을 발급하면 이전 token hash를 덮어쓴다.
- 같은 provider+uid 로그인과 signup 경합에서 DB row lock 이후 상태를 다시 확인한다.
- 삭제 사용자는 자동으로 복구되거나 토큰을 발급받지 않는다.

### Security/Controller 테스트

- 토큰 없이 회원가입 POST를 호출하면 401이다.
- Signup Token으로 같은 provider의 회원가입 POST를 호출할 수 있다.
- Access Token과 Refresh Token으로 회원가입 POST를 호출할 수 없다.
- Signup Token으로 일반 v2 앱 API를 호출할 수 없다.
- Signup Token으로 닉네임 중복 확인·추천 목록 조회 같은 보조 API를 호출할 수 없다.
- Signup Token으로 관리자 API를 호출할 수 없다.
- v2 회원가입 body의 `uid`, `provider`, `role`, `userUuid` 값으로 다른 사용자를 선택할 수 없다.

### 회원가입 서비스 테스트

- principal의 `userUuid`만 가입 대상 식별에 사용한다.
- provider 경로와 DB provider가 다르면 거절한다.
- `PENDING` 사용자만 완료할 수 있다.
- 회원가입 POST 안에서 닉네임 중복과 추천 ID 유효성을 검증하고 잘못된 입력을 명시적인 4xx로
  거절한다.
- 성공 시 프로필·키워드·추천 정보와 `COMPLETED` 상태가 함께 저장된다.
- Signup Token 원문은 저장하지 않고 hash만 Redis에 저장한다.
- 최신 Signup Token만 사용할 수 있고 이전 token은 `SIGNUP_TOKEN_MISMATCH`로 거절한다.
- 성공 시 Signup Token key가 삭제되고 Access/Refresh Token이 발급되며 Refresh Token hash가
  Redis에 저장된다.
- 완료된 사용자에 대한 Signup Token 재사용을 거절한다.
- 같은 Signup Token의 동시 요청 중 하나만 성공한다. 첫 요청이 commit되면 나머지는
  `SIGNUP_ALREADY_COMPLETED`, consume 후 rollback되면 `SIGNUP_TOKEN_MISMATCH`를 반환한다.
- Redis 장애 시 발급과 회원가입이 모두 503으로 실패하고 DB 상태가 잘못 완료되지 않는다.
- token 원자 소비 뒤 DB rollback이 발생하면 재로그인으로만 새 token을 받을 수 있다.
- Redis SET·compare-delete 응답 timeout은 자동 재시도하지 않고 503으로 처리한다.
- 발급 SET 성공 후 DB commit 실패, consume 성공 후 DB rollback·process 중단을 검증한다.
- PENDING login과 signup 경합, 동일 provider+uid 동시 login과 응답 순서 역전을 검증한다.
- Redis 저장 또는 응답 실패 뒤 재로그인하면 정상 토큰을 다시 받을 수 있다.

## 마이그레이션 주의사항

- v1 회원가입 API가 body의 `uid`를 계속 신뢰하는 동안에는 v2 Signup Token 보호를 우회할 수
  있는 legacy 경로가 남는다.
- 앱의 v2 전환율과 v1 회원가입 호출량을 관찰하고, 전환 완료 후 v1 회원가입 endpoint를 우선
  종료한다.
- 과거 JWT 실험용 `/api/v1/auth/token/test`와 `/api/v1/auth/refresh`는 구현 작업에서 삭제한다.
- 구현 순서는 소셜 로그인과 회원가입을 첫 수직 기능으로 삼을 수 있지만, 운영에는 전체 v2
  endpoint와 보안·회귀 테스트를 준비한 서버를 먼저 배포한다. 인증 기능만 별도 출시한 뒤 나머지
  v2를 추가하는 방식은 사용하지 않는다.

## 완료 조건

- Signup Token은 검증된 소셜 로그인 직후 신규 또는 `PENDING` 사용자에게만 발급된다.
- 회원가입 화면 진입만으로 Signup Token을 발급할 수 없다.
- Signup Token으로 provider별 v2 회원가입 POST만 호출할 수 있다.
- 회원가입 전 별도 보조 API 호출 없이 필요한 닉네임·추천 검증을 회원가입 POST 안에서 끝낸다.
- Signup Token으로 일반 앱·관리자 API를 호출할 수 없다.
- 회원가입 body가 호출자 신원 관련 값을 신뢰하지 않는다.
- 회원가입 성공 후 사용자 상태가 `COMPLETED`이고 Access/Refresh Token이 발급된다.
- 만료 또는 중단 후 재로그인하면 같은 pending 사용자에 새 Signup Token을 발급한다.
- 가장 최근에 발급한 Signup Token 하나만 유효하고 회원가입 시 원자적으로 소비된다.
- Signup Token 원문은 서버 저장소와 로그에 남지 않는다.
- v1 legacy 회원가입 우회 경로와 실험용 token endpoint 삭제가 출시 전 체크리스트에 포함된다.

## 범위 밖

- 전체 v2 API endpoint 목록과 공개/인증 매트릭스
- 다중 기기 Refresh Token 정책과 rotation 상세
- CRON/worker 서비스 인증 방식
- 탈퇴 사용자 복구 정책
- v1 종료 일자와 강제 업데이트 정책
- 코드 구현, 배포, 클라이언트 구현
