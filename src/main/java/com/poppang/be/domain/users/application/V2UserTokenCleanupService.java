package com.poppang.be.domain.users.application;

import com.poppang.be.domain.auth.redis.V2RefreshTokenRedisRepository;
import com.poppang.be.domain.auth.redis.V2SignupTokenRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class V2UserTokenCleanupService {

  private static final String CLEANUP_FAILURE_MESSAGE =
      "Authentication store cleanup failed after user deactivation";

  private final V2RefreshTokenRedisRepository refreshTokenRepository;
  private final V2SignupTokenRedisRepository signupTokenRepository;

  void cleanup(String userUuid) {
    boolean cleanupFailed = false;
    try {
      refreshTokenRepository.deleteByUserUuid(userUuid);
    } catch (RuntimeException exception) {
      cleanupFailed = true;
    }

    try {
      signupTokenRepository.deleteByUserUuid(userUuid);
    } catch (RuntimeException exception) {
      cleanupFailed = true;
    }

    if (cleanupFailed) {
      log.warn(CLEANUP_FAILURE_MESSAGE);
    }
  }
}
