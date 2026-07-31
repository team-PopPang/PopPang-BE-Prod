package com.poppang.be.domain.auth.apple.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.poppang.be.domain.auth.application.V2SocialAuthService;
import com.poppang.be.domain.auth.dto.v2.response.V2SocialAuthResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2AppleAuthServiceTest {

  @Mock private AppleCredentialVerifier credentialVerifier;
  @Mock private V2SocialAuthService socialAuthService;

  @Test
  void mobileLoginPassesAuthorizationCodeAndRawNonceToTheV2VerifierFlow() {
    V2SocialAuthResponseDto expected = V2SocialAuthResponseDto.pending("signup-token", 900);
    given(
            socialAuthService.mobileLogin(
                Provider.APPLE, credentialVerifier, "authorization-code", "raw-nonce"))
        .willReturn(expected);

    V2SocialAuthResponseDto result =
        new V2AppleAuthService(credentialVerifier, socialAuthService)
            .mobileLogin("authorization-code", "raw-nonce");

    assertThat(result).isSameAs(expected);
    verify(socialAuthService)
        .mobileLogin(Provider.APPLE, credentialVerifier, "authorization-code", "raw-nonce");
  }
}
