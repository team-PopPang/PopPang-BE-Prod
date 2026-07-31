package com.poppang.be.domain.auth.google.application;

import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.application.V2SocialAuthService;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2SocialAuthResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V2GoogleAuthService {

  private final GoogleCredentialVerifier credentialVerifier;
  private final V2SocialAuthService socialAuthService;

  public V2SocialAuthResponseDto mobileLogin(String idToken) {
    return socialAuthService.mobileLogin(Provider.GOOGLE, credentialVerifier, idToken);
  }

  public V2SocialAuthResponseDto signup(
      String userUuid, V2SignupRequestDto request, JwtAuthenticationDetails details) {
    return socialAuthService.signup(Provider.GOOGLE, userUuid, request, details);
  }
}
