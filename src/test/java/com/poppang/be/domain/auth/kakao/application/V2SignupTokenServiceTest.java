package com.poppang.be.domain.auth.kakao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtProperties;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.domain.auth.redis.TokenHashRecord;
import com.poppang.be.domain.auth.redis.V2SignupTokenRedisRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class V2SignupTokenServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String TOKEN = "header.payload.signature";
  private static final Instant ISSUED_AT = Instant.parse("2026-07-29T00:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2026-07-29T00:17:00Z");

  private final JwtProvider jwtProvider = Mockito.mock(JwtProvider.class);
  private final V2SignupTokenRedisRepository repository =
      Mockito.mock(V2SignupTokenRedisRepository.class);
  private final JwtProperties properties =
      new JwtProperties(
          "01234567890123456789012345678901",
          15,
          30,
          "poppang",
          "poppang-app-v2",
          "poppang-signup-v2",
          17);
  private final V2SignupTokenService service =
      new V2SignupTokenService(jwtProvider, properties, repository);

  @Test
  void issuesSignupTokenAndStoresItsHashAndVerifiedJwtId() {
    VerifiedJwt jwt =
        new VerifiedJwt(
            USER_UUID,
            JwtTokenType.SIGNUP,
            "poppang-signup-v2",
            ISSUED_AT,
            EXPIRES_AT,
            "jwt-id",
            null);
    given(jwtProvider.createSignupToken(USER_UUID)).willReturn(TOKEN);
    given(jwtProvider.verify(TOKEN, JwtTokenType.SIGNUP)).willReturn(jwt);

    V2SignupToken issued = service.issue(USER_UUID);

    assertThat(issued.compactToken()).isEqualTo(TOKEN);
    assertThat(issued.expiresIn()).isEqualTo(17 * 60);

    ArgumentCaptor<TokenHashRecord> record = ArgumentCaptor.forClass(TokenHashRecord.class);
    verify(repository).save(eq(USER_UUID), record.capture());
    assertThat(record.getValue().jwtId()).isEqualTo("jwt-id");
    assertThat(record.getValue().sessionId()).isNull();
    assertThat(record.getValue().toString()).doesNotContain(TOKEN).contains("[REDACTED]");
  }

  @Test
  void redisFailureIsFailClosedWithoutRetry() {
    VerifiedJwt jwt =
        new VerifiedJwt(
            USER_UUID,
            JwtTokenType.SIGNUP,
            "poppang-signup-v2",
            ISSUED_AT,
            EXPIRES_AT,
            "jwt-id",
            null);
    given(jwtProvider.createSignupToken(USER_UUID)).willReturn(TOKEN);
    given(jwtProvider.verify(TOKEN, JwtTokenType.SIGNUP)).willReturn(jwt);
    org.mockito.Mockito.doThrow(new BaseException(ErrorCode.AUTH_STORE_UNAVAILABLE))
        .when(repository)
        .save(eq(USER_UUID), org.mockito.ArgumentMatchers.any());

    assertThatThrownBy(() -> service.issue(USER_UUID))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTH_STORE_UNAVAILABLE);
    verify(jwtProvider, org.mockito.Mockito.times(1)).createSignupToken(USER_UUID);
  }
}
