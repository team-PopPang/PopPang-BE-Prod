package com.poppang.be.domain.popup.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.application.UserAlertService;
import com.poppang.be.domain.alert.application.UserAlertServiceImpl;
import com.poppang.be.domain.alert.application.V2InternalUserAlertService;
import com.poppang.be.domain.alert.application.V2InternalUserAlertServiceImpl;
import com.poppang.be.domain.alert.dto.request.UserAlertRegisterRequestDto;
import com.poppang.be.domain.alert.dto.v2.V2WorkerUserAlertRegisterRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupAlertTargetRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupRegisterRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupRegisterResponseDto;
import com.poppang.be.domain.popup.mapper.PopupUserResponseDtoMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class PopupCollectorMySqlIntegrationTest {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static GenericContainer<?> mysql;
  private static AnnotationConfigApplicationContext context;
  private static JdbcTemplate jdbc;
  private static PopupRegistrationService registration;
  private static PopupAlertTargetService targets;

  @BeforeAll
  static void startDisposableDatabase() {
    // 운영 설정을 읽지 않고 이 테스트가 생성한 컨테이너에만 연결한다.
    String password = UUID.randomUUID().toString();
    mysql =
        new GenericContainer<>("mysql:8.0.35")
            .withEnv("MYSQL_ROOT_PASSWORD", password)
            .withEnv("MYSQL_DATABASE", "collector_test")
            .withEnv("MYSQL_USER", "collector_test")
            .withEnv("MYSQL_PASSWORD", password)
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(2));
    mysql.start();
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/collector_test?useSSL=false&allowPublicKeyRetrieval=true",
            "collector_test",
            password);
    context = new AnnotationConfigApplicationContext();
    context.registerBean(DataSource.class, () -> dataSource);
    context.register(
        DatabaseConfig.class,
        PopupRegistrationService.class,
        PopupRegistrationWriter.class,
        PopupAlertTargetService.class,
        UserAlertServiceImpl.class,
        V2InternalUserAlertServiceImpl.class);
    context.refresh();
    jdbc = new JdbcTemplate(dataSource);
    registration = context.getBean(PopupRegistrationService.class);
    targets = context.getBean(PopupAlertTargetService.class);
  }

  @AfterAll
  static void stopDisposableDatabase() {
    if (context != null) context.close();
    if (mysql != null) mysql.stop();
  }

  @BeforeEach
  void resetFixtures() {
    for (String table :
        List.of(
            "user_alert",
            "user_alert_keyword",
            "popup_image",
            "popup_recommend",
            "popup",
            "users",
            "recommend")) {
      jdbc.update("DELETE FROM " + table);
    }
    jdbc.update(
        "INSERT INTO recommend(id, uuid, recommend_name) VALUES (1, ?, '패션')",
        UUID.randomUUID().toString());
  }

  @Test
  void concurrentRegistrationCreatesOnePopupWithImagesAndRecommendations() throws Exception {
    List<PopupRegisterResponseDto> results =
        simultaneously(
            java.util.stream.IntStream.range(0, 8)
                .<Callable<PopupRegisterResponseDto>>mapToObj(
                    i -> () -> registration.register(request("same-post", "서로 다른 이름 " + i, "요약")))
                .toList());

    assertThat(results).filteredOn(PopupRegisterResponseDto::created).hasSize(1);
    assertThat(results)
        .extracting(PopupRegisterResponseDto::popupUuid)
        .containsOnly(results.get(0).popupUuid());
    assertThat(count("popup")).isEqualTo(1);
    assertThat(count("popup_image")).isEqualTo(2);
    assertThat(count("popup_recommend")).isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT image_url FROM popup_image ORDER BY sort_order", String.class))
        .containsExactly("/images/same-post/1.jpg", "/images/same-post/2.jpg");
    assertThat(
            jdbc.queryForList(
                "SELECT sort_order FROM popup_image ORDER BY sort_order", Integer.class))
        .containsExactly(0, 3);
    String storedName = jdbc.queryForObject("SELECT name FROM popup", String.class);
    PopupRegisterRequestDto duplicate =
        JSON.readValue(
            "{\"instaPostId\":\"same-post\",\"name\":\"덮어쓰면 안 됨\",\"recommendIdList\":[999]}",
            PopupRegisterRequestDto.class);
    assertThat(registration.register(duplicate).created()).isFalse();
    assertThat(jdbc.queryForObject("SELECT name FROM popup", String.class)).isEqualTo(storedName);
    assertThat(count("popup_image")).isEqualTo(2);
  }

  @Test
  void invalidRecommendationRollsBackPopupAndImages() throws Exception {
    PopupRegisterRequestDto invalid = request("rollback-post", "카페", "요약");
    org.springframework.test.util.ReflectionTestUtils.setField(
        invalid, "recommendIdList", List.of(999L));
    assertThatThrownBy(() -> registration.register(invalid))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_RECOMMEND_ID));
    assertThat(count("popup")).isZero();
    assertThat(count("popup_image")).isZero();
    assertThat(count("popup_recommend")).isZero();
  }

  @Test
  void matchesNameAndSummaryFiltersUsersAndRecordsUsersWithoutTokens() throws Exception {
    String first = registration.register(request("first", "성수 카페", "일반 팝업")).popupUuid();
    String second = registration.register(request("second", "패션 팝업", "성수 이벤트")).popupUuid();
    String unrelated = registration.register(request("third", "영화 팝업", "행사 안내")).popupUuid();
    addUser(1, true, false, "fcm-1", "성수", "성수", "카페", " ", "");
    addUser(2, true, false, null, "성수");
    addUser(3, true, false, "   ", "성수");
    addUser(4, false, false, "off-token", "성수");
    addUser(5, true, true, "deleted-token", "성수");
    addUser(6, true, false, "wildcard-token", "%", "_");
    var request = new PopupAlertTargetRequestDto(List.of(second, first, second, unrelated));

    var result = targets.findTargets(request);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).userUuid()).isEqualTo(userUuid(1));
    assertThat(result.get(0).fcmToken()).isEqualTo("fcm-1");
    assertThat(result.get(0).keywords()).containsExactlyInAnyOrder("성수", "카페");
    assertThat(result.get(0).popups())
        .extracting(p -> p.popupUuid())
        .containsExactly(second, first);
    assertThat(result.get(0).popups()).extracting(p -> p.name()).containsExactly("패션 팝업", "성수 카페");
    assertThat(result.get(0).popups()).allMatch(p -> p.region().equals("서울"));
    assertThat(count("user_alert")).isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_alert WHERE read_at IS NULL AND alerted_at IS NOT NULL",
                Long.class))
        .isEqualTo(6);
    assertThat(targets.findTargets(request)).isEqualTo(result);
    assertThat(count("user_alert")).isEqualTo(6);

    String literal = registration.register(request("literal", "100% 할인_행사", "내용")).popupUuid();
    var literalMatches = targets.findTargets(new PopupAlertTargetRequestDto(List.of(literal)));
    assertThat(literalMatches).hasSize(1);
    assertThat(literalMatches.get(0).keywords()).containsExactlyInAnyOrder("%", "_");
    assertThat(count("user_alert")).isEqualTo(7);
  }

  @Test
  void concurrentTargetRequestsAndLegacyWritersStoreEachPairOnce() throws Exception {
    String first = registration.register(request("race1", "카페 하나", "요약")).popupUuid();
    String second = registration.register(request("race2", "카페 둘", "요약")).popupUuid();
    addUser(1, true, false, "fcm-1", "카페");
    UserAlertRegisterRequestDto legacy =
        JSON.readValue("{\"popupUuid\":\"" + first + "\"}", UserAlertRegisterRequestDto.class);
    List<Callable<Boolean>> calls = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      List<String> order = i % 2 == 0 ? List.of(first, second) : List.of(second, first);
      calls.add(
          () -> {
            assertThat(targets.findTargets(new PopupAlertTargetRequestDto(order))).hasSize(1);
            return true;
          });
    }
    calls.add(
        () ->
            allowExisting(
                () ->
                    context
                        .getBean(UserAlertService.class)
                        .registerUserAlert(userUuid(1), legacy)));
    calls.add(
        () ->
            allowExisting(
                () ->
                    context
                        .getBean(V2InternalUserAlertService.class)
                        .registerUserAlert(
                            userUuid(1), new V2WorkerUserAlertRegisterRequestDto(first))));
    assertThat(simultaneously(calls)).containsOnly(true);
    assertThat(count("user_alert")).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT COUNT(*) FROM user_alert GROUP BY users_id, popup_id", Long.class))
        .containsExactly(1L, 1L);
  }

  @Test
  void unknownPopupRollsBackTheWholeRequestAndEmptyOrUnmatchedRequestsReturnEmpty()
      throws Exception {
    String popup = registration.register(request("known", "카페", "요약")).popupUuid();
    addUser(1, true, false, "fcm-1", "카페");
    assertThatThrownBy(
            () ->
                targets.findTargets(
                    new PopupAlertTargetRequestDto(List.of(popup, UUID.randomUUID().toString()))))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POPUP_NOT_FOUND));
    assertThat(count("user_alert")).isZero();
    assertThat(targets.findTargets(new PopupAlertTargetRequestDto(List.of()))).isEmpty();
    String unmatched = registration.register(request("unmatched", "영화", "요약")).popupUuid();
    assertThat(targets.findTargets(new PopupAlertTargetRequestDto(List.of(unmatched)))).isEmpty();
    assertThat(count("user_alert")).isZero();
  }

  private static boolean allowExisting(Runnable write) {
    try {
      write.run();
    } catch (BaseException e) {
      assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_ALERT_ALREADY_EXISTS);
    }
    return true;
  }

  private static <T> List<T> simultaneously(List<Callable<T>> calls) throws Exception {
    var executor = Executors.newFixedThreadPool(calls.size());
    CountDownLatch ready = new CountDownLatch(calls.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> call : calls)
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, SECONDS)) throw new IllegalStateException("동시 요청 시작 시간 초과");
                  return call.call();
                }));
      assertThat(ready.await(10, SECONDS)).isTrue();
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) results.add(future.get(30, SECONDS));
      return results;
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, SECONDS);
    }
  }

  private static PopupRegisterRequestDto request(String postId, String name, String summary)
      throws Exception {
    return JSON.readValue(
        """
        {"name":"%s","startDate":"2026-10-16","endDate":"2026-10-18",
         "openTime":"10:00","closeTime":"20:00","address":"성수","roadAddress":"서울 성동구",
         "region":"서울","instaPostId":"%s","captionSummary":"%s","caption":"원문",
         "mediaType":"IMAGE","isActive":true,"recommendIdList":[1],
         "imageList":[{"imageUrl":"/images/%s/1.jpg"},{"imageUrl":"/images/%s/2.jpg","sortOrder":3}]}
        """
            .formatted(name, postId, summary, postId, postId),
        PopupRegisterRequestDto.class);
  }

  private static String userUuid(int id) {
    return "00000000-0000-0000-0000-%012d".formatted(id);
  }

  private static void addUser(
      int id, boolean alerted, boolean deleted, String token, String... keywords) {
    jdbc.update(
        "INSERT INTO users(id, uuid, is_alerted, is_deleted, fcm_token, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
        id,
        userUuid(id),
        alerted,
        deleted,
        token);
    for (String keyword : keywords)
      jdbc.update(
          "INSERT INTO user_alert_keyword(users_id, alert_keyword) VALUES (?, ?)", id, keyword);
  }

  private static long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableJpaRepositories(
      basePackages = {
        "com.poppang.be.domain.popup.infrastructure", "com.poppang.be.domain.alert.infrastructure",
        "com.poppang.be.domain.users.infrastructure",
            "com.poppang.be.domain.recommend.infrastructure",
        "com.poppang.be.domain.favorite.infrastructure"
      })
  @EnableJpaAuditing
  @EnableTransactionManagement
  static class DatabaseConfig {
    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan("com.poppang.be.domain");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto",
              "create-drop",
              "hibernate.physical_naming_strategy",
              "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    PopupUserResponseDtoMapper popupUserResponseDtoMapper() {
      return mock(PopupUserResponseDtoMapper.class);
    }
  }
}
