package com.poppang.be.domain.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;
import com.poppang.be.domain.users.entity.SignupOs;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class SignupOsPersistenceIntegrationTest {

  private static GenericContainer<?> mysql;
  private static StandardServiceRegistry registry;
  private static SessionFactory sessions;
  private static String jdbcUrl;
  private static final String PASSWORD = "disposable-test-password";

  @BeforeAll
  static void startDisposableDatabaseAndApplyMigration() throws Exception {
    mysql =
        new GenericContainer<>(DockerImageName.parse("mysql:8.0.43"))
            .withEnv("MYSQL_ROOT_PASSWORD", PASSWORD)
            .withEnv("MYSQL_DATABASE", "signup_test")
            .withEnv("MYSQL_USER", "signup_test")
            .withEnv("MYSQL_PASSWORD", PASSWORD)
            .withExposedPorts(3306)
            .withStartupTimeout(Duration.ofMinutes(2));
    mysql.start();
    jdbcUrl =
        "jdbc:mysql://"
            + mysql.getHost()
            + ":"
            + mysql.getMappedPort(3306)
            + "/signup_test?allowPublicKeyRetrieval=true&useSSL=false";

    // 운영 접속이나 private 설정 없이 이전 users 스키마와 기존 회원을 구성한다.
    try (Connection connection = connection();
        Statement sql = connection.createStatement()) {
      sql.execute(
          """
          CREATE TABLE users (
            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
            uid VARCHAR(255), uuid VARCHAR(36) NOT NULL,
            provider ENUM('APPLE','GOOGLE','KAKAO'), email VARCHAR(255),
            nickname VARCHAR(255) UNIQUE, role ENUM('ADMIN','MEMBER'),
            signup_status ENUM('COMPLETED','PENDING'),
            is_alerted BIT, fcm_token VARCHAR(255), is_deleted BIT,
            created_at DATETIME(6) NOT NULL, updated_at DATETIME(6)
          )
          """);
      sql.execute(
          """
          INSERT INTO users (uid, uuid, signup_status, created_at)
          VALUES ('legacy', 'legacy', 'COMPLETED', NOW())
          """);
      String migration =
          Files.readString(Path.of("docs/database/signup-os/01-users-expand.sql"))
              .replaceAll("(?m)--[^\\r\\n]*", "");
      for (String command : migration.split(";")) {
        if (!command.isBlank()) {
          sql.execute(command);
        }
      }
    }

    registry =
        new StandardServiceRegistryBuilder()
            .applySettings(
                Map.of(
                    "hibernate.connection.url", jdbcUrl,
                    "hibernate.connection.username", "signup_test",
                    "hibernate.connection.password", PASSWORD,
                    "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.connection.isolation", "4"))
            .build();
    sessions =
        new MetadataSources(registry)
            .addAnnotatedClass(Users.class)
            .buildMetadata()
            .buildSessionFactory();
  }

  @AfterAll
  static void stopDisposableDatabase() {
    if (sessions != null) sessions.close();
    if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    if (mysql != null) mysql.stop();
  }

  @Test
  void migrationKeepsExistingUsersUnknownAndColumnNullable() throws Exception {
    try (Connection connection = connection();
        Statement sql = connection.createStatement()) {
      try (ResultSet row =
          sql.executeQuery(
              """
          SELECT is_nullable, column_default, column_type FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'signup_os'
          """)) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString("is_nullable")).isEqualTo("YES");
        assertThat(row.getString("column_default")).isNull();
        assertThat(row.getString("column_type")).isEqualTo("enum('IOS','ANDROID')");
      }
      try (ResultSet row = sql.executeQuery("SELECT signup_os FROM users WHERE uid = 'legacy'")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString(1)).isNull();
      }
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"IOS"})
  void concurrentSignupPreservesFirstCompletionIncludingUnknownOs(String firstOs) throws Exception {
    String uid = insertPending();
    var executor = Executors.newSingleThreadExecutor();
    CountDownLatch competingRequestStarted = new CountDownLatch(1);
    CountDownLatch competingRequestReadUser = new CountDownLatch(1);
    try (Session first = sessions.openSession()) {
      first.beginTransaction();
      Users user = repository(first).findByUidForUpdate(uid).orElseThrow();
      user.completeSignup(request(firstOs));
      first.flush();

      var second =
          executor.submit(
              () -> {
                try (Session session = sessions.openSession()) {
                  session.beginTransaction();
                  competingRequestStarted.countDown();
                  Users locked = repository(session).findByUidForUpdate(uid).orElseThrow();
                  competingRequestReadUser.countDown();
                  locked.completeSignup(request("ANDROID"));
                  session.getTransaction().commit();
                }
              });
      assertThat(competingRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(competingRequestReadUser.await(300, TimeUnit.MILLISECONDS)).isFalse();
      first.getTransaction().commit();
      second.get(10, TimeUnit.SECONDS);
      assertThat(read(uid).getSignupOs()).isEqualTo(SignupOs.fromNullable(firstOs));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void staleProfileUpdateCannotEraseRecordedSignupOs() throws Exception {
    String uid = insertPending();
    try (Session stale = sessions.openSession();
        Session signup = sessions.openSession()) {
      stale.beginTransaction();
      Users staleUser = repository(stale).findByUid(uid).orElseThrow();

      signup.beginTransaction();
      Users user = repository(signup).findByUidForUpdate(uid).orElseThrow();
      user.completeSignup(request("IOS"));
      signup.getTransaction().commit();

      staleUser.updateFcmToken("new-device-token");
      stale.getTransaction().commit();
    }
    Users saved = read(uid);
    assertThat(saved.getSignupOs()).isEqualTo(SignupOs.IOS);
    assertThat(saved.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(saved.getFcmToken()).isEqualTo("new-device-token");
  }

  @Test
  void rolledBackSignupDoesNotRecordOsOrCompleteUser() throws Exception {
    String uid = insertPending();
    try (Session session = sessions.openSession()) {
      session.beginTransaction();
      Users user = repository(session).findByUidForUpdate(uid).orElseThrow();
      user.completeSignup(request("ANDROID"));
      session.flush();
      session.getTransaction().rollback();
    }
    Users saved = read(uid);
    assertThat(saved.getSignupOs()).isNull();
    assertThat(saved.getSignupStatus()).isEqualTo(SignupStatus.PENDING);
  }

  private static String insertPending() throws Exception {
    String uid = UUID.randomUUID().toString();
    try (Connection connection = connection();
        var insert =
            connection.prepareStatement(
                """
        INSERT INTO users (uid, uuid, provider, role, signup_status, is_alerted, is_deleted, created_at)
        VALUES (?, ?, 'KAKAO', 'MEMBER', 'PENDING', 0, 0, NOW())
        """)) {
      insert.setString(1, uid);
      insert.setString(2, uid);
      insert.executeUpdate();
    }
    return uid;
  }

  private Users read(String uid) {
    try (Session session = sessions.openSession()) {
      return repository(session).findByUid(uid).orElseThrow();
    }
  }

  private static Connection connection() throws Exception {
    return DriverManager.getConnection(jdbcUrl, "signup_test", PASSWORD);
  }

  private static UsersRepository repository(Session session) {
    return new JpaRepositoryFactory(session).getRepository(UsersRepository.class);
  }

  private SignupRequestDto request(String os) {
    SignupRequestDto request = new SignupRequestDto();
    ReflectionTestUtils.setField(request, "nickname", UUID.randomUUID().toString());
    ReflectionTestUtils.setField(request, "signupOs", os);
    return request;
  }
}
