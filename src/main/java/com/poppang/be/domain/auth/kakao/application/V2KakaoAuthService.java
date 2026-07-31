package com.poppang.be.domain.auth.kakao.application;

import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.application.V2TokenService;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2KakaoAuthResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V2KakaoAuthService {

  private final KakaoCredentialVerifier credentialVerifier;
  private final V2KakaoLoginWriter loginWriter;
  private final V2KakaoSignupWriter signupWriter;
  private final V2TokenService tokenService;

  public V2KakaoAuthResponseDto mobileLogin(String providerAccessToken) {
    VerifiedSocialIdentity identity =
        credentialVerifier.verifyMobileCredential(providerAccessToken);
    V2KakaoLoginResult result;
    try {
      result = loginWriter.login(identity);
    } catch (DataIntegrityViolationException exception) {
      result = loginWriter.recoverAfterCreateCollision(identity);
    }
    return loginResponse(result);
  }

  public V2KakaoAuthResponseDto signup(
      String userUuid, V2SignupRequestDto request, JwtAuthenticationDetails details) {
    V2AuthUserResponseDto user = signupWriter.completeSignup(userUuid, request, details);
    return V2KakaoAuthResponseDto.completed(user, tokenService.issueTokens(userUuid));
  }

  private V2KakaoAuthResponseDto loginResponse(V2KakaoLoginResult result) {
    if (result.signupStatus() == com.poppang.be.domain.users.entity.SignupStatus.PENDING) {
      V2SignupToken signupToken = result.signupToken();
      return V2KakaoAuthResponseDto.pending(signupToken.compactToken(), signupToken.expiresIn());
    }
    return V2KakaoAuthResponseDto.completed(
        result.user(), tokenService.issueTokens(result.user().userUuid()));
  }
}
