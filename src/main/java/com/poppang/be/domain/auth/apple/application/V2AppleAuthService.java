package com.poppang.be.domain.auth.apple.application;

import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.application.V2SocialAuthService;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2SocialAuthResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V2AppleAuthService {

  private final AppleCredentialVerifier credentialVerifier;
  private final V2SocialAuthService socialAuthService;

  public V2SocialAuthResponseDto mobileLogin(String authorizationCode, String rawNonce) {
    return socialAuthService.mobileLogin(
        Provider.APPLE, credentialVerifier, authorizationCode, rawNonce);
  }

  public V2SocialAuthResponseDto signup(
      String userUuid, V2SignupRequestDto request, JwtAuthenticationDetails details) {
    return socialAuthService.signup(Provider.APPLE, userUuid, request, details);
  }
}
