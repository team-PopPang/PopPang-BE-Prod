# README 개편 설계

## 목적

`README.md`를 신규 백엔드 개발자가 저장소의 역할, 실행 조건, 주요 구조를 빠르게 파악할 수 있는 온보딩 문서로 전면 개편한다. 현재 코드와 운영 방식에 맞지 않는 초기 설명은 제거하고, 세부 개발 규칙과 배포 절차는 각각 `AGENTS.md`와 `DEPLOYMENT.md`로 연결한다.

## 대상 독자와 성공 기준

주 독자는 PopPang 백엔드에 처음 참여하는 개발자다. README를 읽은 개발자는 다음을 이해할 수 있어야 한다.

- 이 저장소가 제공하는 기능과 제공하지 않는 기능
- 현재 기술 스택과 외부 의존 서비스
- 로컬 실행 전에 필요한 private config와 profile 주의사항
- 빌드, 테스트, 포맷, 로컬 실행 명령
- 주요 패키지와 도메인의 책임
- API 문서 위치를 확인하는 방법
- 상세 개발 규칙과 배포 절차를 찾을 문서

## 문서 역할과 우선순위

- `README.md`: 프로젝트 소개와 개발 시작점
- `AGENTS.md`: 코드 구조, 구현 관례, 보안 및 도메인별 주의사항의 기준 문서
- `DEPLOYMENT.md`: 배포, 검증, 롤백과 운영 위험의 기준 문서

README에는 다른 두 문서의 내용을 장황하게 복제하지 않는다. 내용이 충돌하면 `AGENTS.md`와 `DEPLOYMENT.md`를 우선하도록 안내한다.

## 정보 구조

README는 다음 순서로 구성한다.

1. 프로젝트 소개와 저장소 범위
2. 주요 기능과 시스템 경계
3. 기술 스택
4. 아키텍처와 도메인 구조
5. 로컬 개발 사전 조건
6. private config와 profile 주의사항
7. 빌드, 테스트, 포맷, 로컬 실행 명령
8. 주요 API 경로와 OpenAPI 문서 확인 방법
9. 인증, 푸시, 테스트 범위 등 현재 구현상 주의사항
10. 배포와 개발 규칙 문서 링크

문서 초반에는 제품과 저장소의 범위를 설명하고, 중반에는 개발자가 실제로 실행하는 흐름을 배치한다. 구현 세부와 운영 위험은 필요한 만큼만 요약한 뒤 기준 문서로 연결한다.

## 핵심 내용

### 프로젝트와 기능

PopPang은 사용자가 관심 있는 팝업 스토어를 조회하고, 찜과 관심 키워드 및 인앱 알림 데이터를 관리하는 모바일 앱 백엔드다. iOS와 Android뿐 아니라 일부 웹 및 관리자 화면을 위한 REST API도 제공한다.

기능은 팝업 조회·검색·추천·필터, 소셜 로그인, 회원 관리, 찜, 키워드, 인앱 알림함, 팝업 제보와 관리자 처리, Redis 기반 조회수 누적으로 요약한다.

시스템 경계는 명확히 적는다. 이 저장소는 FCM 토큰과 키워드 대상 데이터를 관리하지만 Firebase/APNs 푸시를 직접 발송하지 않는다. 외부 cron 또는 worker가 대상 조회 API를 폴링하는 구조다. 추천 카테고리 마스터와 팝업 개인화 추천의 구현 위치도 구분한다.

### 기술 스택과 구조

버전은 저장소에서 확인한 값을 사용한다.

- Java 17
- Spring Boot 3.5.6
- Gradle Wrapper 8.14.3
- Spring Data JPA, MySQL
- Redis/Lettuce, Spring Scheduling
- Spring Security, 자체 JWT
- Kakao, Google, Apple 소셜 로그인
- SpringDoc OpenAPI, Actuator, Jakarta Mail
- Spotless, google-java-format 1.17.0

패키지 트리는 `common`과 `domain`의 최상위 책임 및 일곱 개 도메인만 보여준다. `popup`의 app/web 분리, `auth`의 provider별 구조처럼 이해에 필요한 예외만 덧붙인다. 엔티티와 repository 전체 목록은 싣지 않는다.

### 실행과 설정

클린 클론에는 ignored private config가 없을 수 있음을 먼저 알린다. `application*.yml`, Apple `.p8`, `.env`의 실제 값이나 복사 가능한 비밀값 예시는 README에 넣지 않는다.

기본 실행에는 MySQL, Redis와 JWT의 secret, access-token 만료, refresh-token 만료, issuer 설정이 필요하다고 설명한다. 소셜 로그인과 신규 가입 메일 등 해당 기능을 검증하려면 OAuth provider, Apple key, SMTP 설정도 필요하다고 구분한다. 단일 `JWT_SECRET`만으로 충분하다고 안내하지 않는다.

기본 profile이 `prod`이므로 로컬 실행 명령에는 반드시 `local` profile을 명시한다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

빌드와 검증 명령은 실제 Gradle task를 그대로 사용한다.

```bash
./gradlew clean bootJar
./gradlew build
./gradlew test
./gradlew spotlessApply
./gradlew spotlessCheck
```

테스트와 빌드도 private DB, Redis, JWT 설정이 필요할 수 있음을 경고한다. 안전한 local/test 설정을 확인하지 않은 상태에서 운영 자원에 연결될 수 있는 명령을 실행하지 않도록 안내한다.

### API와 운영 문서

모든 endpoint를 나열하지 않고 인증, 비회원 팝업, 회원 팝업, 웹 팝업, 제보·관리, 회원, 찜, 키워드, 알림함, 추천 카테고리의 대표 prefix를 표로 제공한다.

기본 Swagger URL을 고정하지 않는다. 실제 OpenAPI UI와 JSON 경로는 private `application*.yml`의 `springdoc.swagger-ui.path`, `springdoc.api-docs.path`에서 확인하도록 한다.

배포 명령은 README에 요약만 두고 `DEPLOYMENT.md`로 연결한다. 특히 `make`만 실행하면 private config 다운로드와 실제 배포가 연속 실행될 수 있으므로, README에서 무심코 실행할 일반 개발 명령으로 제시하지 않는다.

### 현재 구현상 주의사항

자체 JWT 발급과 Redis refresh token 저장 기능은 존재하지만 일반 소셜 로그인 응답 전체에 일관되게 연결된 상태는 아님을 짧게 밝힌다. URL 수준의 보안도 전체 API를 일괄 차단하는 형태가 아니므로, README에서 모든 API가 JWT로 보호된다고 표현하지 않는다. Swagger의 `@Hidden`은 문서 노출 제어일 뿐 보안 경계가 아니라는 점은 `AGENTS.md`로 연결한다.

테스트는 현재 팝업 제보·관리, 조회수 가산, 이미지 저장소와 application context 확인에 집중되어 있다. 테스트 개수를 고정해 적거나 전체 도메인이 검증되었다고 과장하지 않는다.

## 제거할 내용

- 백엔드가 직접 푸시 알림을 발송한다는 설명
- `application.yml` 하나와 `ddl-auto: update`를 제안하는 설정 예시
- 잘못된 JAR 이름과 고정된 기본 Swagger URL
- JWT refresh token, 관리자 API, 테스트, CI/CD가 아직 없다는 낡은 로드맵
- 현재 구현보다 과장된 개인화 추천 설명
- 저장소에서 관리하지 않는 계획성 체크리스트

## 실패 상황 안내

README가 예방해야 할 대표 실패는 다음과 같다.

- private config가 없는 클린 클론에서 빌드나 실행이 실패함
- local profile을 생략해 기본 prod profile로 실행함
- DB, Redis 또는 OAuth 설정 일부만 구성해 application context가 뜨지 않음
- 기본 `/swagger-ui/index.html`을 열어 문서가 없다고 오해함
- `make`를 일반 빌드 명령으로 오해해 배포까지 실행함
- 이 백엔드가 푸시 발송까지 담당한다고 오해함

각 실패는 짧은 경고와 올바른 확인 위치로 안내한다. 상세 복구 절차는 README 범위를 넘으므로 운영 런북으로 연결한다.

## 검증 방법

README 수정 후 다음을 확인한다.

- 모든 상대 링크가 실제 파일을 가리키는지 확인
- 기술 버전과 명령을 `build.gradle`, Gradle wrapper, workflow와 대조
- 주요 경로를 controller annotation과 대조
- 비밀값이나 환경별 URL이 포함되지 않았는지 확인
- 미완성 표시, 낡은 로드맵, 고정 Swagger URL, 직접 푸시 발송 설명이 남지 않았는지 검색
- Markdown 코드 블록과 표가 정상적으로 닫혔는지 확인
- `git diff --check`로 공백 오류 확인

README만 변경하므로 애플리케이션 테스트 통과를 완료 조건으로 삼지는 않는다. 다만 문서에 적은 Gradle 명령이 실제 task와 일치하는지는 정적으로 검증한다.

## 비목표

- 전체 API 명세 복제
- 엔티티 관계도나 데이터베이스 스키마 문서화
- 배포 스크립트 또는 CI/CD 수정
- 현재 인증·보안·푸시 구조 개선
- local용 private config 생성 또는 비밀값 예시 제공
