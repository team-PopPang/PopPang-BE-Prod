package com.poppang.be.domain.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.poppang.be.domain.auth.redis.V2RefreshTokenRedisRepository;
import com.poppang.be.domain.auth.redis.V2SignupTokenRedisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class V2UserTokenCleanupServiceTest {

  private static final String SENSITIVE_USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private V2RefreshTokenRedisRepository refreshTokenRepository;
  @Mock private V2SignupTokenRedisRepository signupTokenRepository;

  @Test
  void deletesBothRefreshAndSignupKeys() {
    V2UserTokenCleanupService cleanupService =
        new V2UserTokenCleanupService(refreshTokenRepository, signupTokenRepository);

    cleanupService.cleanup(SENSITIVE_USER_UUID);

    verify(refreshTokenRepository).deleteByUserUuid(SENSITIVE_USER_UUID);
    verify(signupTokenRepository).deleteByUserUuid(SENSITIVE_USER_UUID);
  }

  @Test
  void cleanupFailureDoesNotFailDeactivationAndDoesNotExposeIdentifiers() {
    willThrow(new RuntimeException("credential-must-not-be-logged"))
        .given(refreshTokenRepository)
        .deleteByUserUuid(SENSITIVE_USER_UUID);
    willThrow(new RuntimeException("token-must-not-be-logged"))
        .given(signupTokenRepository)
        .deleteByUserUuid(SENSITIVE_USER_UUID);
    V2UserTokenCleanupService cleanupService =
        new V2UserTokenCleanupService(refreshTokenRepository, signupTokenRepository);
    Logger logger = (Logger) LoggerFactory.getLogger(V2UserTokenCleanupService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      assertThatCode(() -> cleanupService.cleanup(SENSITIVE_USER_UUID)).doesNotThrowAnyException();
    } finally {
      logger.detachAppender(appender);
    }

    verify(refreshTokenRepository).deleteByUserUuid(SENSITIVE_USER_UUID);
    verify(signupTokenRepository).deleteByUserUuid(SENSITIVE_USER_UUID);
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .isEqualTo("Authentication store cleanup failed after user deactivation")
        .doesNotContain(
            SENSITIVE_USER_UUID,
            "credential-must-not-be-logged",
            "token-must-not-be-logged",
            "email",
            "fcm");
  }
}
