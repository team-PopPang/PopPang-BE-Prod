package com.poppang.be.domain.auth.redis;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class V2TokenRedisRepositoryTest {

  private static final String USER_UUID = "user-uuid";
  private static final Instant ISSUED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(900);

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private RedisConnection redisConnection;
  @Mock private RedisStringCommands stringCommands;

  private V2RefreshTokenRedisRepository refreshRepository;
  private V2SignupTokenRedisRepository signupRepository;

  @BeforeEach
  void setUp() {
    refreshRepository = new V2RefreshTokenRedisRepository(redisTemplate);
    signupRepository = new V2SignupTokenRedisRepository(redisTemplate);
  }

  @Test
  void storesRefreshAndSignupRecordsWithAbsoluteJwtExpiration() {
    TokenHashRecord refresh = refreshRecord('a', "refresh-jti");
    TokenHashRecord signup = signupRecord('b', "signup-jti");
    when(redisConnection.stringCommands()).thenReturn(stringCommands);
    when(stringCommands.set(any(), any(), any(), eq(SetOption.UPSERT))).thenReturn(true);
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenAnswer(
            invocation ->
                ((RedisCallback<Boolean>) invocation.getArgument(0)).doInRedis(redisConnection));

    refreshRepository.save(USER_UUID, refresh);
    signupRepository.save(USER_UUID, signup);

    ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
    ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
    ArgumentCaptor<Expiration> expirationCaptor = ArgumentCaptor.forClass(Expiration.class);
    verify(stringCommands, times(2))
        .set(
            keyCaptor.capture(),
            valueCaptor.capture(),
            expirationCaptor.capture(),
            eq(SetOption.UPSERT));

    assertThat(keyCaptor.getAllValues())
        .extracting(bytes -> new String(bytes, StandardCharsets.UTF_8))
        .containsExactly("auth:v2:refresh:" + USER_UUID, "auth:v2:signup:" + USER_UUID);
    assertThat(valueCaptor.getAllValues())
        .extracting(bytes -> new String(bytes, StandardCharsets.UTF_8))
        .containsExactly(refresh.serializedValue(), signup.serializedValue());
    assertThat(expirationCaptor.getAllValues())
        .allSatisfy(
            expiration -> {
              assertThat(expiration.isUnixTimestamp()).isTrue();
              assertThat(expiration.getConverted(MILLISECONDS))
                  .isEqualTo(EXPIRES_AT.toEpochMilli());
            });
  }

  @Test
  void rotatesRefreshOnlyWhenCurrentRecordMatches() {
    TokenHashRecord current = refreshRecord('a', "current-jti");
    TokenHashRecord replacement = refreshRecord('b', "replacement-jti");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
        .thenReturn(1L, 0L);

    assertThat(refreshRepository.rotate(USER_UUID, current, replacement)).isTrue();
    assertThat(refreshRepository.rotate(USER_UUID, current, replacement)).isFalse();

    verify(redisTemplate, times(2))
        .execute(
            any(RedisScript.class),
            eq(List.of("auth:v2:refresh:" + USER_UUID)),
            eq(current.serializedValue()),
            eq(replacement.serializedValue()),
            eq(Long.toString(replacement.expiresAt().toEpochMilli())));
  }

  @Test
  void rejectsRefreshRotationWithoutAReplacementToken() {
    TokenHashRecord current = refreshRecord('a', "current-jti");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> refreshRepository.rotate(USER_UUID, current, current));
  }

  @Test
  void compareDeletesRefreshSessionAndSignupRecord() {
    TokenHashRecord refresh = refreshRecord('a', "refresh-jti");
    TokenHashRecord signup = signupRecord('b', "signup-jti");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L, 0L);

    assertThat(refreshRepository.deleteIfSessionMatches(USER_UUID, refresh.sessionId())).isTrue();
    assertThat(signupRepository.consume(USER_UUID, signup)).isFalse();
  }

  @Test
  void mapsRedisConnectionFailureToStoreUnavailableInsteadOfMismatch() {
    TokenHashRecord refresh = refreshRecord('a', "refresh-jti");
    when(redisTemplate.execute(any(RedisCallback.class)))
        .thenThrow(new RedisConnectionFailureException("down"));

    assertThatThrownBy(() -> refreshRepository.save(USER_UUID, refresh))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_STORE_UNAVAILABLE));
  }

  @Test
  void mapsRedisTimeoutWithoutRetryingRotation() {
    TokenHashRecord current = refreshRecord('a', "current-jti");
    TokenHashRecord replacement = refreshRecord('b', "replacement-jti");
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
        .thenThrow(new QueryTimeoutException("timeout"));

    assertThatThrownBy(() -> refreshRepository.rotate(USER_UUID, current, replacement))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_STORE_UNAVAILABLE));
    verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(), any(), any());
  }

  private TokenHashRecord refreshRecord(char hashCharacter, String jwtId) {
    return new TokenHashRecord(
        String.valueOf(hashCharacter).repeat(64), jwtId, "session-id", ISSUED_AT, EXPIRES_AT);
  }

  private TokenHashRecord signupRecord(char hashCharacter, String jwtId) {
    return new TokenHashRecord(
        String.valueOf(hashCharacter).repeat(64), jwtId, null, ISSUED_AT, EXPIRES_AT);
  }
}
