package com.poppang.be.domain.auth.redis;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.poppang.be.common.jwt.JwtFingerprint;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class V2TokenRedisRepositoryIntegrationTest {

  private static final int CONCURRENT_REQUESTS = 16;
  private static final String REDIS_VERSION_ENV = "POPPANG_TEST_REDIS_VERSION";
  private static final String DEFAULT_REDIS_VERSION = "7.2.12";

  private static GenericContainer<?> redis;
  private static LettuceConnectionFactory connectionFactory;
  private static RedisTemplate<String, String> redisTemplate;
  private static V2RefreshTokenRedisRepository refreshRepository;
  private static V2SignupTokenRedisRepository signupRepository;

  @BeforeAll
  static void startDisposableRedis() {
    String configuredRedisVersion = System.getenv(REDIS_VERSION_ENV);
    String redisVersion =
        configuredRedisVersion == null || configuredRedisVersion.isBlank()
            ? DEFAULT_REDIS_VERSION
            : configuredRedisVersion;
    assertThat(redisVersion).matches("[0-9]+\\.[0-9]+(\\.[0-9]+)?");

    redis =
        new GenericContainer<>(DockerImageName.parse("redis:" + redisVersion))
            .withExposedPorts(6379);
    redis.start();

    RedisStandaloneConfiguration configuration =
        new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
    connectionFactory = new LettuceConnectionFactory(configuration);
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();

    redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(connectionFactory);
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.setValueSerializer(new StringRedisSerializer());
    redisTemplate.afterPropertiesSet();

    refreshRepository = new V2RefreshTokenRedisRepository(redisTemplate);
    signupRepository = new V2SignupTokenRedisRepository(redisTemplate);
  }

  @AfterAll
  static void stopDisposableRedis() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
    if (redis != null) {
      redis.stop();
    }
  }

  @Test
  void storesOnlyFingerprintWithExpirationBoundToJwt() {
    String userUuid = UUID.randomUUID().toString();
    String rawToken = "signup.header.payload.signature";
    Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = issuedAt.plusSeconds(60);
    TokenHashRecord record =
        TokenHashRecord.from(
            rawToken,
            verified(JwtTokenType.SIGNUP, userUuid, "signup-jti", null, issuedAt, expiresAt));

    signupRepository.save(userUuid, record);

    String key = V2SignupTokenRedisRepository.KEY_PREFIX + userUuid;
    String storedValue = redisTemplate.opsForValue().get(key);
    Long ttlMillis = redisTemplate.getExpire(key, MILLISECONDS);
    assertThat(storedValue)
        .contains(JwtFingerprint.sha256(rawToken), "signup-jti")
        .doesNotContain(rawToken);
    assertThat(ttlMillis).isNotNull().isPositive().isLessThanOrEqualTo(60_000L);
  }

  @Test
  void concurrentRefreshRotationAllowsExactlyOneRequest() throws Exception {
    String userUuid = UUID.randomUUID().toString();
    String sessionId = UUID.randomUUID().toString();
    Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    TokenHashRecord current =
        record(
            "current-refresh-token",
            verified(
                JwtTokenType.REFRESH,
                userUuid,
                "current-jti",
                sessionId,
                issuedAt,
                issuedAt.plusSeconds(120)));
    refreshRepository.save(userUuid, current);

    List<Boolean> results =
        runConcurrently(
            index -> {
              Instant replacementIssuedAt = issuedAt.plusSeconds(1);
              TokenHashRecord replacement =
                  record(
                      "replacement-refresh-token-" + index,
                      verified(
                          JwtTokenType.REFRESH,
                          userUuid,
                          "replacement-jti-" + index,
                          sessionId,
                          replacementIssuedAt,
                          replacementIssuedAt.plusSeconds(240)));
              return refreshRepository.rotate(userUuid, current, replacement);
            });

    assertThat(results).containsOnlyOnce(true);
    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    Long rotatedTtl =
        redisTemplate.getExpire(V2RefreshTokenRedisRepository.KEY_PREFIX + userUuid, MILLISECONDS);
    assertThat(rotatedTtl).isNotNull().isGreaterThan(180_000L).isLessThanOrEqualTo(241_000L);
  }

  @Test
  void concurrentSignupConsumeAllowsExactlyOneRequest() throws Exception {
    String userUuid = UUID.randomUUID().toString();
    Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    TokenHashRecord signup =
        record(
            "signup-token",
            verified(
                JwtTokenType.SIGNUP,
                userUuid,
                "signup-jti",
                null,
                issuedAt,
                issuedAt.plusSeconds(120)));
    signupRepository.save(userUuid, signup);

    List<Boolean> results = runConcurrently(index -> signupRepository.consume(userUuid, signup));

    assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
  }

  @Test
  void logoutDeletesOnlyTheMatchingCurrentSession() {
    String userUuid = UUID.randomUUID().toString();
    String sessionId = UUID.randomUUID().toString();
    Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    TokenHashRecord refresh =
        record(
            "refresh-token",
            verified(
                JwtTokenType.REFRESH,
                userUuid,
                "refresh-jti",
                sessionId,
                issuedAt,
                issuedAt.plusSeconds(120)));
    refreshRepository.save(userUuid, refresh);

    assertThat(refreshRepository.deleteIfSessionMatches(userUuid, UUID.randomUUID().toString()))
        .isFalse();
    assertThat(refreshRepository.deleteIfSessionMatches(userUuid, sessionId)).isTrue();
    assertThat(refreshRepository.deleteIfSessionMatches(userUuid, sessionId)).isFalse();
  }

  private static TokenHashRecord record(String rawToken, VerifiedJwt verifiedJwt) {
    return TokenHashRecord.from(rawToken, verifiedJwt);
  }

  private static VerifiedJwt verified(
      JwtTokenType tokenType,
      String userUuid,
      String jwtId,
      String sessionId,
      Instant issuedAt,
      Instant expiresAt) {
    return new VerifiedJwt(
        userUuid,
        tokenType,
        tokenType == JwtTokenType.SIGNUP ? "signup-audience" : "app-audience",
        issuedAt,
        expiresAt,
        jwtId,
        sessionId);
  }

  private static List<Boolean> runConcurrently(ConcurrentCall concurrentCall) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
    CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Callable<Boolean>> calls = new ArrayList<>();
      for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
        int requestIndex = index;
        calls.add(
            () -> {
              ready.countDown();
              start.await();
              return concurrentCall.execute(requestIndex);
            });
      }
      List<Future<Boolean>> futures = calls.stream().map(executor::submit).toList();
      ready.await();
      start.countDown();

      List<Boolean> results = new ArrayList<>();
      for (Future<Boolean> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  @FunctionalInterface
  private interface ConcurrentCall {
    boolean execute(int requestIndex);
  }
}
