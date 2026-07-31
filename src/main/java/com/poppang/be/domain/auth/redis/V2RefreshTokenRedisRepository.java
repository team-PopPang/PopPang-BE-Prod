package com.poppang.be.domain.auth.redis;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Repository;

@Repository
public class V2RefreshTokenRedisRepository {

  static final String KEY_PREFIX = "auth:v2:refresh:";

  private static final RedisScript<Long> ROTATE_SCRIPT = script("redis/refresh-rotate.lua");
  private static final RedisScript<Long> COMPARE_DELETE_SCRIPT =
      script("redis/refresh-compare-delete.lua");
  private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

  private final RedisTemplate<String, String> redisTemplate;

  public V2RefreshTokenRedisRepository(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = Objects.requireNonNull(redisTemplate);
  }

  public void save(String userUuid, TokenHashRecord record) {
    requireRefreshRecord(record);
    saveAt(key(userUuid), record);
  }

  public boolean rotate(String userUuid, TokenHashRecord current, TokenHashRecord replacement) {
    requireRefreshRecord(current);
    requireRefreshRecord(replacement);
    if (!current.sessionId().equals(replacement.sessionId())) {
      throw new IllegalArgumentException("Refresh rotation must keep the same sessionId");
    }
    if (current.serializedValue().equals(replacement.serializedValue())) {
      throw new IllegalArgumentException("Refresh rotation requires a new token record");
    }
    return executeBoolean(
        ROTATE_SCRIPT,
        key(userUuid),
        current.serializedValue(),
        replacement.serializedValue(),
        Long.toString(replacement.expiresAt().toEpochMilli()));
  }

  public boolean deleteIfSessionMatches(String userUuid, String sessionId) {
    if (sessionId == null || sessionId.isBlank() || sessionId.contains(":")) {
      throw new IllegalArgumentException("sessionId must be non-blank and must not contain ':'");
    }
    return executeBoolean(COMPARE_DELETE_SCRIPT, key(userUuid), sessionId);
  }

  public boolean deleteByUserUuid(String userUuid) {
    try {
      return Boolean.TRUE.equals(redisTemplate.delete(key(userUuid)));
    } catch (DataAccessException exception) {
      throw storeUnavailable();
    }
  }

  private void saveAt(String key, TokenHashRecord record) {
    try {
      RedisCallback<Boolean> saveCallback =
          connection ->
              connection
                  .stringCommands()
                  .set(
                      STRING_SERIALIZER.serialize(key),
                      STRING_SERIALIZER.serialize(record.serializedValue()),
                      Expiration.unixTimestamp(record.expiresAt().toEpochMilli(), MILLISECONDS),
                      SetOption.UPSERT);
      Boolean saved = redisTemplate.execute(saveCallback);
      if (!Boolean.TRUE.equals(saved)) {
        throw storeUnavailable();
      }
    } catch (DataAccessException exception) {
      throw storeUnavailable();
    }
  }

  private boolean executeBoolean(RedisScript<Long> script, String key, String... arguments) {
    try {
      Long result = redisTemplate.execute(script, List.of(key), (Object[]) arguments);
      if (result == null) {
        throw storeUnavailable();
      }
      return result == 1L;
    } catch (DataAccessException exception) {
      throw storeUnavailable();
    }
  }

  private String key(String userUuid) {
    if (userUuid == null || userUuid.isBlank()) {
      throw new IllegalArgumentException("userUuid must not be blank");
    }
    return KEY_PREFIX + userUuid;
  }

  private void requireRefreshRecord(TokenHashRecord record) {
    if (record == null || record.sessionId() == null) {
      throw new IllegalArgumentException("Refresh record requires a sessionId");
    }
  }

  private BaseException storeUnavailable() {
    return new BaseException(ErrorCode.AUTH_STORE_UNAVAILABLE);
  }

  private static RedisScript<Long> script(String location) {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(location));
    script.setResultType(Long.class);
    return script;
  }
}
