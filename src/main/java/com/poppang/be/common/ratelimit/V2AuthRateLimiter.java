package com.poppang.be.common.ratelimit;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtFingerprint;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class V2AuthRateLimiter {

  static final String KEY_PREFIX = "auth:v2:rate:";

  private static final int WINDOW_SECONDS = 60;
  private static final RedisScript<Long> ACQUIRE_SCRIPT = script();

  private final RedisTemplate<String, String> redisTemplate;
  private final V2AuthRateLimitProperties properties;

  public V2AuthRateLimiter(
      RedisTemplate<String, String> redisTemplate, V2AuthRateLimitProperties properties) {
    this.redisTemplate = Objects.requireNonNull(redisTemplate);
    this.properties = Objects.requireNonNull(properties);
  }

  public void check(V2AuthRateLimitScope scope, String identifier) {
    if (scope == null || identifier == null || identifier.isBlank()) {
      throw new IllegalArgumentException("Rate limit scope and identifier must not be blank");
    }

    Long result;
    try {
      result =
          redisTemplate.execute(
              ACQUIRE_SCRIPT,
              List.of(key(scope, identifier)),
              Integer.toString(WINDOW_SECONDS),
              Integer.toString(properties.limitFor(scope)));
    } catch (DataAccessException exception) {
      throw new BaseException(ErrorCode.AUTH_STORE_UNAVAILABLE);
    }

    if (result == null) {
      throw new BaseException(ErrorCode.AUTH_STORE_UNAVAILABLE);
    }
    if (result != 1L) {
      throw new BaseException(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
  }

  private String key(V2AuthRateLimitScope scope, String identifier) {
    return KEY_PREFIX
        + scope.name().toLowerCase(Locale.ROOT)
        + ":"
        + JwtFingerprint.sha256(identifier);
  }

  private static RedisScript<Long> script() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("redis/auth-rate-limit.lua"));
    script.setResultType(Long.class);
    return script;
  }
}
