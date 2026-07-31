package com.poppang.be.common.ratelimit;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class V2AuthRateLimiterTest {

  @Mock private RedisTemplate<String, String> redisTemplate;

  private V2AuthRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    rateLimiter =
        new V2AuthRateLimiter(redisTemplate, new V2AuthRateLimitProperties(null, null, null));
  }

  @Test
  void defaultsAreLoginTenSignupFiveAndRefreshTenPerMinute() {
    V2AuthRateLimitProperties properties = new V2AuthRateLimitProperties(null, null, null);

    assertThat(properties.limitFor(V2AuthRateLimitScope.LOGIN)).isEqualTo(10);
    assertThat(properties.limitFor(V2AuthRateLimitScope.SIGNUP)).isEqualTo(5);
    assertThat(properties.limitFor(V2AuthRateLimitScope.REFRESH)).isEqualTo(10);
  }

  @Test
  void rejectsNonPositiveConfiguredLimits() {
    assertThatIllegalArgumentException().isThrownBy(() -> new V2AuthRateLimitProperties(0, 5, 10));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new V2AuthRateLimitProperties(10, -1, 10));
    assertThatIllegalArgumentException().isThrownBy(() -> new V2AuthRateLimitProperties(10, 5, 0));
  }

  @Test
  void storesOnlyHashedIdentityAndUsesTheConfiguredFixedWindow() {
    String clientIp = "203.0.113.27";
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

    rateLimiter.check(V2AuthRateLimitScope.LOGIN, clientIp);

    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
    verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), eq("60"), eq("10"));
    assertThat(keys.getValue()).singleElement().asString().doesNotContain(clientIp);
    assertThat(keys.getValue().get(0)).matches("auth:v2:rate:login:[0-9a-f]{64}");
  }

  @Test
  void rejectsRequestsBeyondTheLimit() {
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

    assertError(
        () -> rateLimiter.check(V2AuthRateLimitScope.SIGNUP, "user-uuid"),
        ErrorCode.RATE_LIMIT_EXCEEDED);
  }

  @Test
  void mapsRedisTimeoutToAuthenticationStoreUnavailableWithoutRetry() {
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
        .thenThrow(new QueryTimeoutException("timeout"));

    assertError(
        () -> rateLimiter.check(V2AuthRateLimitScope.REFRESH, "user-uuid"),
        ErrorCode.AUTH_STORE_UNAVAILABLE);
    verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(), any());
  }

  private void assertError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }
}
