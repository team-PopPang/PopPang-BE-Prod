package com.poppang.be.domain.auth.redis;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenRedisRepository {
  void save(String userUuid, String refreshToken, Duration ttl);

  Optional<String> find(String userUuid);

  void delete(String userUuid);
}
