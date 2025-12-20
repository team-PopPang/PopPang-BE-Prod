# PopPang Back-End (팝팡 백엔드)

> 관심있는 팝업 스토어 정보를 가장 먼저 알려주는 서비스, PopPang의 백엔드 레포지토리입니다.

## 📢 프로젝트 소개

PopPang은 사용자가 등록한 키워드와 관련된 팝업 스토어 정보가 새로 생겼을 때, 놓치지 않도록 알림을 보내주는 모바일 애플리케이션입니다. 본 레포지토리는 PopPang의 iOS 및 Android 클라이언트를 위한 API 서버입니다.

## ✨ 주요 기능

- **소셜 로그인**: Kakao, Google, Apple 계정을 통한 간편한 회원가입 및 로그인을 지원합니다.
- **키워드 등록 및 알림**: 사용자가 관심 키워드를 등록하면, 해당 키워드를 포함하는 새로운 팝업 스토어 정보가 등록될 시 푸시 알림을 전송합니다.
- **팝업 스토어 정보 제공**: 현재 진행 중이거나 예정된 팝업 스토어의 상세 정보를 조회할 수 있습니다.
- **찜하기**: 관심 있는 팝업 스토어를 '찜'하여 별도로 관리할 수 있습니다.
- **팝업 스토어 추천**: 사용자의 활동 데이터를 기반으로 좋아할 만한 팝업 스토어를 추천합니다.

## 🛠️ 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 3.x
- **Data**: Spring Data JPA, MySQL
- **API Documentation**: SpringDoc OpenAPI (Swagger UI)
- **Build Tool**: Gradle

## 🚀 시작하기

### 1. 레포지토리 클론

```bash
git clone https://github.com/poppang/PopPang-BE.git
cd PopPang-BE
```

### 2. 설정 파일 생성

`src/main/resources/` 경로에 `application.yml` 파일을 생성하고 아래와 같이 데이터베이스 및 OAuth 설정 등을 추가합니다. (보안 정보는 별도 관리 필요)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/poppang?useSSL=false&serverTimezone=Asia/Seoul
    username: [db_username]
    password: [db_password]
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
# ... 기타 OAuth 및 JWT 설정
```

### 3. 애플리케이션 실행

```bash
./gradlew build
java -jar build/libs/be-0.0.1-SNAPSHOT.jar
```

애플리케이션 실행 후, 아래 URL에서 API 문서를 확인할 수 있습니다.

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## 📁 프로젝트 구조

```
src/main/java/com/poppang/be
├── common          # 공통 모듈 (BaseEntity, Enum, Config 등)
└── domain          # 도메인별 비즈니스 로직
    ├── auth        # 인증 및 소셜 로그인
    ├── favorite    # 찜하기 기능
    ├── keyword     # 사용자 키워드 관리
    ├── popup       # 팝업 스토어 정보
    ├── recommend   # 추천 기능
    └── users       # 사용자 정보
```

## 📝 추후 개발 예정

- [ ] 고도화된 보안 적용 (JWT 리프레시 토큰 등)
- [ ] 관리자 페이지 개발
- [ ] 알림 기능 고do화 (알림 시간 설정, 방식 선택 등)
- [ ] 테스트 코드 작성 및 CI/CD 파이프라인 구축
- [ ] 성능 개선 및 모니터링 환경 구축