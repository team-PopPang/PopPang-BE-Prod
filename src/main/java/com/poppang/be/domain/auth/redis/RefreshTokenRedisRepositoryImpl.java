package com.poppang.be.domain.auth.redis;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepositoryImpl implements RefreshTokenRedisRepository {

  private static final String KEY_PREFIX = "auth:refresh:";

  private final RedisTemplate<String, String> redisTemplate;

  private String key(String userUuid) {
    return KEY_PREFIX + userUuid;
  }

  @Override
  public void save(String userUuid, String refreshToken, Duration ttl) {
    redisTemplate.opsForValue().set(key(userUuid), refreshToken, ttl);
  }

  @Override
  public Optional<String> find(String userUuid) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(key(userUuid)));
  }

  @Override
  public void delete(String userUuid) {
    redisTemplate.delete(key(userUuid));
  }
}
