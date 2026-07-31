package com.poppang.be.common.ratelimit;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.rate-limit")
public record V2AuthRateLimitProperties(
    Integer loginPerMinute, Integer signupPerMinute, Integer refreshPerMinute) {

  private static final int DEFAULT_LOGIN_PER_MINUTE = 10;
  private static final int DEFAULT_SIGNUP_PER_MINUTE = 5;
  private static final int DEFAULT_REFRESH_PER_MINUTE = 10;

  public V2AuthRateLimitProperties {
    loginPerMinute = withDefault(loginPerMinute, DEFAULT_LOGIN_PER_MINUTE);
    signupPerMinute = withDefault(signupPerMinute, DEFAULT_SIGNUP_PER_MINUTE);
    refreshPerMinute = withDefault(refreshPerMinute, DEFAULT_REFRESH_PER_MINUTE);
  }

  public int limitFor(V2AuthRateLimitScope scope) {
    return switch (Objects.requireNonNull(scope)) {
      case LOGIN -> loginPerMinute;
      case SIGNUP -> signupPerMinute;
      case REFRESH -> refreshPerMinute;
    };
  }

  private static int withDefault(Integer configured, int defaultValue) {
    int value = configured == null ? defaultValue : configured;
    if (value <= 0) {
      throw new IllegalArgumentException("Auth rate limit must be positive");
    }
    return value;
  }
}
