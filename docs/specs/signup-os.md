# 최초 가입 OS 기록

## 상태와 범위

IMPLEMENTED. v1 카카오·구글·애플 회원가입 요청에 선택 필드 `signupOs`를 추가했다.
사용자가 승인한 기준은 최초 회원가입 완료 시점의 OS 보존이다.
기존 URL, 응답, 인증 방식과 v2 가입 요청 계약은 유지한다. DB에는 컬럼 추가 SQL만
준비하며 운영 실행은 이 작업에 포함하지 않는다.

## API 계약

대상은 `POST /api/v1/auth/{provider}/signup`의 `kakao`, `google`, `apple` 경로다.
기존 JSON 본문에 `"signupOs": "IOS"` 또는 `"signupOs": "ANDROID"`를 추가할 수 있다.

- 필드 생략 또는 JSON `null`: 기존 가입 흐름을 유지하고 가입 OS는 `NULL`이다.
- 문자열 `IOS`, `ANDROID`: 최초 가입 완료 시 해당 값을 저장한다.
- 빈 문자열, 공백, 소문자, `AOS` 등 지원하지 않는 문자열: HTTP 400,
  `INVALID_SIGNUP_OS`(4206)를 반환한다. 닉네임 등 기존 검증 순서는 유지한다.
- 응답 DTO에는 필드를 추가하지 않는다.

## 데이터와 보존 규칙

`users.signup_os`는 nullable `ENUM('IOS', 'ANDROID')`, 기본값은 `NULL`이다.
Java에서는 문자열 enum으로 매핑한다. `provider`는 소셜 제공자, `signup_os`는 가입
완료 기기의 OS로 서로 다른 의미다.

가입 대기 상태에서 완료 상태로 바뀔 때 기존 프로필·키워드·추천 정보와 같은 DB
트랜잭션으로 저장한다. 트랜잭션이 실패하면 OS도 롤백한다.
가입 완료 이후 로그인, 프로필 변경, FCM 갱신, 가입 API 재호출로 값을 변경하지 않는다.
구버전 앱에서 OS 없이 가입한 사용자가 나중에 값을 보내도 `NULL`을 유지한다.
기존 완료 회원의 OS는 추정하거나 현재 기기 정보로 채우지 않는다.

v1의 기존 가입 재호출 동작을 새 오류로 차단하지 않고 새 필드만 보존한다.
동시 가입 요청은 동일 회원 행의 쓰기 잠금으로 직렬화하고, 잠금 후 읽은 가입 상태로
최초 완료 여부를 판단한다. 일반 회원 변경은 변경된 컬럼만 갱신해, 가입 전에 읽은
오래된 OS 값이 다른 프로필 변경과 함께 다시 기록되지 않게 한다.
기존 nullable `signup_status` 값은 기존 가입 완료 메서드와 마찬가지로 완료 전 상태로
취급하되, 이미 기록된 OS는 보존한다.

## 배포와 호환성

1. [컬럼 추가 SQL](../database/signup-os/01-users-expand.sql)을 대상 DB에서 검토·적용한다.
2. optional 필드를 받는 서버를 배포한다. 운영 설정은 `ddl-auto: validate`이므로
   컬럼 추가 전에 새 서버를 배포하면 안 된다.
3. iOS·Android 앱의 기존 가입 요청에 OS를 추가한다.

구버전 앱 및 현재 v2 가입은 OS를 보내지 않으므로 `NULL`로 남는다.
별도 테이블, 필수값 전환, 기존 회원 backfill, 새 endpoint, 전략 패턴 리팩터링은 없다.
서버 롤백 시 새 컬럼을 유지한다. 컬럼 삭제는 수집한 데이터를 잃으므로 롤백 절차에
자동으로 포함하지 않는다.

## 검증 기준

- 세 경로에서 IOS·ANDROID 저장과 필드 생략·null 요청의 기존 응답을 확인한다.
- 잘못된 OS는 400이며 회원 상태 변경·관련 저장·메일 발송이 없어야 한다.
- 재가입 요청에서 기존 값 및 이미 완료된 미확인 값(NULL)을 유지한다.
- 같은 회원의 동시 가입, 다른 프로필 갱신과의 경합, 트랜잭션 롤백을 검증한다.
- 컬럼 추가 SQL과 ORM 매핑은 폐기 가능한 MySQL에서 확인한다. 운영 DB 검증과
  실제 클라이언트 배포 확인은 별도다.

## 검증 결과

- 2026-10-12 인증·회원 관련 38개 테스트 클래스의 169개 테스트 통과(실패·건너뜀 0).
  실행: `./gradlew test --tests 'com.poppang.be.domain.auth.*' --tests 'com.poppang.be.domain.users.*' --tests '*V1AuthImplementationIsolationContractTest' --no-daemon`
- 신규 HTTP 테스트 15개로 세 v1 경로의 저장·생략·null·400·재호출 보존을 확인했다.
- 폐기용 MySQL 8.0.43 통합 테스트 5개로 SQL 적용, ORM validate, 기존 데이터 NULL,
  동시 가입, 오래된 FCM 갱신과의 경합, 롤백을 확인했다.
- `./gradlew spotlessCheck --no-daemon` 및 `git diff --check` 통과.
- 운영 DB SQL 실행, 서버 배포, 실제 iOS·Android 앱 연동은 수행하지 않았다.
