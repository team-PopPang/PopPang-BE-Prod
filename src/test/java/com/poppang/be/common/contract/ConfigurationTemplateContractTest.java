package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.poppang.be.common.jwt.JwtProperties;
import com.poppang.be.common.security.WorkerApiKeyProperties;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import com.poppang.be.domain.auth.config.QaTokenProperties;
import com.poppang.be.domain.auth.google.config.GoogleProperties;
import com.poppang.be.domain.auth.kakao.config.KakaoProperties;
import com.poppang.be.domain.popup.application.PopupSubmissionImageStorageProperties;
import com.poppang.be.domain.popup.application.PopupSubmissionImageStorageProperties.StorageType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/**
 * 커밋하는 설정 템플릿(application.yml, application-prod.yml)이 실제 값 없이 런타임 env 계약만 참조하는지 검사한다. 비밀 env 이름은
 * AWS-Ops `scripts/host/runtime-cache.py`의 BE 집합(be.env)과 같다. 실패 메시지에는 키 이름만 남기고 값은 출력하지 않는다.
 */
class ConfigurationTemplateContractTest {

  private static final Path COMMON_TEMPLATE = Path.of("src/main/resources/application.yml");
  private static final Path PROD_TEMPLATE = Path.of("src/main/resources/application-prod.yml");

  private static final Set<String> BE_SECRET_ENV =
      Set.of(
          "APPLE_CLIENT_ID",
          "APPLE_KEY_ID",
          "APPLE_REDIRECT_URI",
          "APPLE_TEAM_ID",
          "APPLE_TOKEN_URI",
          "APPLE_WEB_CLIENT_ID",
          "GOOGLE_CLIENT_ID",
          "GOOGLE_CLIENT_SECRET",
          "GOOGLE_IOS_CLIENT_ID",
          "GOOGLE_REDIRECT_URI",
          "GOOGLE_SCOPE",
          "GOOGLE_TOKEN_URI",
          "GOOGLE_USER_INFO_URI",
          "INTERNAL_WORKER_API_KEY",
          "JWT_ACCESS_TOKEN_EXP_MINUTES",
          "JWT_AUDIENCE",
          "JWT_ISSUER",
          "JWT_REFRESH_TOKEN_EXP_DAYS",
          "JWT_SECRET",
          "JWT_SIGNUP_AUDIENCE",
          "JWT_SIGNUP_TOKEN_EXP_MINUTES",
          "KAKAO_CLIENT_ID",
          "KAKAO_REDIRECT_URI",
          "KAKAO_TOKEN_URI",
          "KAKAO_USER_INFO_URI",
          "MYSQL_DATABASE",
          "MYSQL_PASSWORD",
          "MYSQL_USER",
          "POPPANG_MAIL_ADMINS",
          "POPPANG_MAIL_FROM",
          "POPPANG_MAIL_PASSWORD",
          "QA_AUTH_ADMIN_USER_UUID",
          "QA_AUTH_API_KEY",
          "QA_AUTH_MEMBER_USER_UUID",
          "REDIS_PASSWORD",
          "SPRINGDOC_API_DOCS_ENABLED",
          "SPRINGDOC_API_DOCS_PATH",
          "SPRINGDOC_SWAGGER_UI_ENABLED",
          "SPRINGDOC_SWAGGER_UI_PATH",
          "SPRINGDOC_SWAGGER_UI_URL");

  /** compose environment로 주는 비밀 아닌 env. 값이 있는 기본값은 이 표에 있는 것만 허용한다. */
  private static final Map<String, String> NON_SECRET_ENV_DEFAULTS =
      Map.ofEntries(
          Map.entry("MYSQL_HOST", ""),
          Map.entry("MYSQL_PORT", "3306"),
          Map.entry("REDIS_HOST", ""),
          Map.entry("REDIS_PORT", "6379"),
          Map.entry("APPLE_PRIVATE_KEY_PATH", ""),
          Map.entry("SUBMISSION_IMAGE_S3_BUCKET", ""),
          Map.entry("SUBMISSION_IMAGE_S3_REGION", ""),
          Map.entry("SUBMISSION_IMAGE_ROOT", "/opt/submission_images"),
          Map.entry("SUBMISSION_IMAGE_URL_PREFIX", "/submissionImages"),
          Map.entry("MULTIPART_MAX_FILE_SIZE", "10MB"),
          Map.entry("MULTIPART_MAX_REQUEST_SIZE", "30MB"));

  /** 2026-10-02 미니 PC 운영 JAR·컨테이너에서 확인한 비밀 아닌 운영 값. prod 프로필에서 그대로 이어받는다. */
  private static final Map<String, String> PRODUCTION_NON_SECRET_SETTINGS =
      Map.ofEntries(
          Map.entry("spring.profiles.default", "prod"),
          Map.entry("spring.application.name", "poppang-be-prod"),
          Map.entry("spring.servlet.multipart.max-file-size", "10MB"),
          Map.entry("spring.servlet.multipart.max-request-size", "30MB"),
          Map.entry("server.forward-headers-strategy", "framework"),
          Map.entry("server.tomcat.remoteip.remote-ip-header", "X-Forwarded-For"),
          Map.entry("server.tomcat.remoteip.protocol-header", "X-Forwarded-Proto"),
          Map.entry("server.tomcat.remoteip.trusted-proxies", ".*"),
          Map.entry("logging.level.org.springframework.security", "DEBUG"),
          Map.entry("logging.level.io.lettuce.core", "DEBUG"),
          Map.entry("logging.level.org.springframework.data.redis", "DEBUG"),
          Map.entry("logging.level.org.springframework.boot.context.config", "DEBUG"),
          Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
          Map.entry("spring.jpa.show-sql", "true"),
          Map.entry("spring.jpa.properties.hibernate.format_sql", "true"),
          Map.entry("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"),
          Map.entry(
              "spring.autoconfigure.exclude",
              "org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration"),
          Map.entry("app.storage.submission-image-root", "/opt/submission_images"),
          Map.entry("app.storage.submission-image-url-prefix", "/submissionImages"));

  private static final String PRODUCTION_JDBC_PARAMETERS =
      "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";

  private static final List<String> SECRET_PROPERTY_PREFIXES =
      List.of(
          "kakao.",
          "google.",
          "apple.",
          "jwt.",
          "poppang.mail.",
          "qa.auth.",
          "internal.worker.",
          "springdoc.");

  private static final Set<String> SECRET_SPRING_PROPERTIES =
      Set.of(
          "spring.datasource.username",
          "spring.datasource.password",
          "spring.data.redis.password",
          "spring.data.redis.host");

  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");
  private static final Pattern PURE_PLACEHOLDER = Pattern.compile("\\$\\{[A-Z0-9_]+}");
  private static final Pattern FORBIDDEN_LITERAL =
      Pattern.compile(
          "(?i)poppang\\.co\\.kr|zapto|no-ip|@|-----BEGIN|password=|"
              + "(?<![0-9.])[0-9]{1,3}(\\.[0-9]{1,3}){3}(?![0-9.])|jdbc:mysql://(?!\\$\\{MYSQL_HOST})");

  @Test
  void templatesReferenceExactlyTheRuntimeEnvContract() throws IOException {
    Map<String, String> placeholders = new HashMap<>();
    for (Path template : List.of(COMMON_TEMPLATE, PROD_TEMPLATE)) {
      for (Object value : flatten(template).values()) {
        Matcher matcher = PLACEHOLDER.matcher(String.valueOf(value));
        while (matcher.find()) {
          placeholders.put(matcher.group(1), matcher.group(2) == null ? "" : matcher.group(2));
        }
      }
    }

    Set<String> expected = new TreeSet<>(BE_SECRET_ENV);
    expected.addAll(NON_SECRET_ENV_DEFAULTS.keySet());
    assertThat(new TreeSet<>(placeholders.keySet())).isEqualTo(expected);

    List<String> invalidDefaults = new ArrayList<>();
    placeholders.forEach(
        (name, defaultValue) -> {
          String allowed = BE_SECRET_ENV.contains(name) ? "" : NON_SECRET_ENV_DEFAULTS.get(name);
          if (!defaultValue.equals(allowed)) {
            invalidDefaults.add(name);
          }
        });
    assertThat(invalidDefaults).as("Placeholders with unapproved defaults").isEmpty();
  }

  @Test
  void secretPropertiesArePurePlaceholdersAndNoLiteralLooksSecret() throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path template : List.of(COMMON_TEMPLATE, PROD_TEMPLATE)) {
      for (Map.Entry<String, Object> entry : flatten(template).entrySet()) {
        String key = entry.getKey();
        String value = String.valueOf(entry.getValue());
        boolean secretProperty =
            SECRET_SPRING_PROPERTIES.contains(key)
                || SECRET_PROPERTY_PREFIXES.stream().anyMatch(key::startsWith);
        if (secretProperty && !PURE_PLACEHOLDER.matcher(value).matches()) {
          violations.add(template.getFileName() + ":" + key);
        }
        if (FORBIDDEN_LITERAL.matcher(value).find()) {
          violations.add(template.getFileName() + ":" + key);
        }
      }
    }
    assertThat(violations).as("Template keys with literal secrets or addresses").isEmpty();
  }

  @Test
  void templatesHaveNoDraftOrTodoMarkers() throws IOException {
    for (Path template : List.of(COMMON_TEMPLATE, PROD_TEMPLATE)) {
      assertThat(Files.readString(template))
          .as(template.getFileName().toString())
          .doesNotContainIgnoringCase("DRAFT")
          .doesNotContainIgnoringCase("TODO");
    }
  }

  @Test
  void commonTemplateResolvesWithoutRuntimeEnvironment() throws IOException {
    StandardEnvironment environment = isolatedEnvironment();
    load(environment, COMMON_TEMPLATE);

    for (String key : flatten(COMMON_TEMPLATE).keySet()) {
      assertThatCode(() -> environment.getProperty(key)).as(key).doesNotThrowAnyException();
    }
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto", "none"))
        .isIn("none", "validate");
  }

  @Test
  void prodProfileCarriesOverProductionNonSecretSettings() throws IOException {
    StandardEnvironment environment = isolatedEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("fake-env", fakeEnvironment()));
    load(environment, PROD_TEMPLATE);
    load(environment, COMMON_TEMPLATE);

    List<String> mismatched = new ArrayList<>();
    PRODUCTION_NON_SECRET_SETTINGS.forEach(
        (key, expected) -> {
          if (!expected.equals(environment.getProperty(key))) {
            mismatched.add(key);
          }
        });
    assertThat(mismatched).as("Production settings not carried over").isEmpty();
    assertThat(environment.getProperty("spring.datasource.url"))
        .endsWith("/fake_database" + PRODUCTION_JDBC_PARAMETERS);
  }

  @Test
  void prodProfileBindsApplicationPropertiesFromRuntimeEnvironment() throws IOException {
    StandardEnvironment environment = isolatedEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("fake-env", fakeEnvironment()));
    load(environment, PROD_TEMPLATE);
    load(environment, COMMON_TEMPLATE);
    Binder binder = Binder.get(environment);

    assertThat(environment.getProperty("spring.datasource.url"))
        .startsWith("jdbc:mysql://fake-mysql:3306/fake_database");
    assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("fake_user");
    assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("fake-db-pass");
    assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("fake-redis");
    assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6379");
    assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("fake-redis-pass");
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isIn("none", "validate");
    assertThat(environment.getProperty("poppang.mail.from")).isEqualTo("fake-from");
    assertThat(environment.getProperty("springdoc.swagger-ui.path")).isEqualTo("/fake-ui");

    JwtProperties jwt = binder.bind("jwt", JwtProperties.class).get();
    assertThat(jwt.issuer()).isEqualTo("fake-issuer");
    assertThat(jwt.accessTokenExpMinutes()).isEqualTo(30);
    assertThat(binder.bind("qa.auth", QaTokenProperties.class).get().memberUserUuid())
        .isEqualTo("44444444-4444-4444-4444-444444444444");
    assertThat(binder.bind("internal.worker", WorkerApiKeyProperties.class).get().apiKey())
        .isEqualTo("fake-worker-api-key-0123456789-abcdef");
    AppleProperties apple = binder.bind("apple", AppleProperties.class).get();
    assertThat(apple.getPrivateKeyPath()).isEqualTo("/run/secrets/apple.p8");
    assertThat(apple.getKeyId()).isEqualTo("fake-key-id");
    assertThat(binder.bind("kakao", KakaoProperties.class).get().getClientId())
        .isEqualTo("fake-kakao-client");
    assertThat(binder.bind("google", GoogleProperties.class).get().getIosClientId())
        .isEqualTo("fake-google-ios");

    PopupSubmissionImageStorageProperties storage =
        binder.bind("app.storage", PopupSubmissionImageStorageProperties.class).get();
    assertThat(storage.submissionImageStorageType()).isEqualTo(StorageType.S3);
    assertThat(storage.submissionImageS3Bucket()).isEqualTo("fake-bucket");
    assertThat(storage.submissionImageS3Region()).isEqualTo("fake-region-1");
    assertThat(storage.normalizedSubmissionImageUrlPrefix()).isEqualTo("/submissionImages");
  }

  private static Map<String, Object> fakeEnvironment() {
    Map<String, Object> values = new HashMap<>();
    for (String name : BE_SECRET_ENV) {
      values.put(name, "fake-" + name.toLowerCase().replace('_', '-'));
    }
    values.put("JWT_SECRET", "fake-jwt-secret-0123456789-abcdefghijklmnop");
    values.put("JWT_ACCESS_TOKEN_EXP_MINUTES", "30");
    values.put("JWT_REFRESH_TOKEN_EXP_DAYS", "14");
    values.put("JWT_SIGNUP_TOKEN_EXP_MINUTES", "15");
    values.put("JWT_ISSUER", "fake-issuer");
    values.put("JWT_AUDIENCE", "fake-audience");
    values.put("JWT_SIGNUP_AUDIENCE", "fake-signup-audience");
    values.put("QA_AUTH_API_KEY", "fake-qa-api-key-0123456789-abcdefgh");
    values.put("QA_AUTH_MEMBER_USER_UUID", "44444444-4444-4444-4444-444444444444");
    values.put("QA_AUTH_ADMIN_USER_UUID", "55555555-5555-5555-5555-555555555555");
    values.put("INTERNAL_WORKER_API_KEY", "fake-worker-api-key-0123456789-abcdef");
    values.put("SPRINGDOC_API_DOCS_ENABLED", "false");
    values.put("SPRINGDOC_SWAGGER_UI_ENABLED", "false");
    values.put("SPRINGDOC_SWAGGER_UI_PATH", "/fake-ui");
    values.put("POPPANG_MAIL_FROM", "fake-from");
    values.put("KAKAO_CLIENT_ID", "fake-kakao-client");
    values.put("GOOGLE_IOS_CLIENT_ID", "fake-google-ios");
    values.put("APPLE_KEY_ID", "fake-key-id");
    values.put("MYSQL_DATABASE", "fake_database");
    values.put("MYSQL_USER", "fake_user");
    values.put("MYSQL_PASSWORD", "fake-db-pass");
    values.put("REDIS_PASSWORD", "fake-redis-pass");
    values.put("MYSQL_HOST", "fake-mysql");
    values.put("REDIS_HOST", "fake-redis");
    values.put("APPLE_PRIVATE_KEY_PATH", "/run/secrets/apple.p8");
    values.put("SUBMISSION_IMAGE_S3_BUCKET", "fake-bucket");
    values.put("SUBMISSION_IMAGE_S3_REGION", "fake-region-1");
    return values;
  }

  private static StandardEnvironment isolatedEnvironment() {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    return environment;
  }

  private static void load(StandardEnvironment environment, Path template) throws IOException {
    for (PropertySource<?> source :
        new YamlPropertySourceLoader()
            .load(template.toString(), new FileSystemResource(template.toFile()))) {
      environment.getPropertySources().addLast(source);
    }
  }

  private static Map<String, Object> flatten(Path template) throws IOException {
    Map<String, Object> values = new LinkedHashMap<>();
    for (PropertySource<?> source :
        new YamlPropertySourceLoader()
            .load(template.toString(), new FileSystemResource(template.toFile()))) {
      EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
      for (String name : enumerable.getPropertyNames()) {
        values.put(name, enumerable.getProperty(name));
      }
    }
    return values;
  }
}
