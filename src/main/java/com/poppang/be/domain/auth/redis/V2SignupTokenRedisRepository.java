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
public class V2SignupTokenRedisRepository {

  static final String KEY_PREFIX = "auth:v2:signup:";

  private static final RedisScript<Long> COMPARE_DELETE_SCRIPT =
      script("redis/signup-compare-delete.lua");
  private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

  private final RedisTemplate<String, String> redisTemplate;

  public V2SignupTokenRedisRepository(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = Objects.requireNonNull(redisTemplate);
  }

  public void save(String userUuid, TokenHashRecord record) {
    requireSignupRecord(record);
    saveAt(key(userUuid), record);
  }

  public boolean consume(String userUuid, TokenHashRecord record) {
    requireSignupRecord(record);
    try {
      Long result =
          redisTemplate.execute(
              COMPARE_DELETE_SCRIPT, List.of(key(userUuid)), record.serializedValue());
      if (result == null) {
        throw storeUnavailable();
      }
      return result == 1L;
    } catch (DataAccessException exception) {
      throw storeUnavailable();
    }
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

  private String key(String userUuid) {
    if (userUuid == null || userUuid.isBlank()) {
      throw new IllegalArgumentException("userUuid must not be blank");
    }
    return KEY_PREFIX + userUuid;
  }

  private void requireSignupRecord(TokenHashRecord record) {
    if (record == null || record.sessionId() != null) {
      throw new IllegalArgumentException("Signup record must not have a sessionId");
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
