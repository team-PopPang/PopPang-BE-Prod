package com.poppang.be.domain.auth.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2SocialAuthResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.SignupStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V2SocialAuthService {

  private final V2SocialLoginWriter loginWriter;
  private final V2SocialSignupWriter signupWriter;
  private final V2TokenService tokenService;

  public V2SocialAuthResponseDto mobileLogin(
      Provider expectedProvider,
      ProviderCredentialVerifier credentialVerifier,
      String providerCredential) {
    return mobileLogin(
        expectedProvider, credentialVerifier.verifyMobileCredential(providerCredential));
  }

  public V2SocialAuthResponseDto mobileLogin(
      Provider expectedProvider,
      ProviderCredentialVerifier credentialVerifier,
      String providerCredential,
      String rawNonce) {
    return mobileLogin(
        expectedProvider, credentialVerifier.verifyMobileCredential(providerCredential, rawNonce));
  }

  private V2SocialAuthResponseDto mobileLogin(
      Provider expectedProvider, VerifiedSocialIdentity identity) {
    if (identity.provider() != expectedProvider) {
      throw new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
    }

    V2SocialLoginResult result;
    try {
      result = loginWriter.login(expectedProvider, identity);
    } catch (DataIntegrityViolationException exception) {
      result = loginWriter.recoverAfterCreateCollision(expectedProvider, identity);
    }
    return loginResponse(result);
  }

  public V2SocialAuthResponseDto signup(
      Provider expectedProvider,
      String userUuid,
      V2SignupRequestDto request,
      JwtAuthenticationDetails details) {
    V2AuthUserResponseDto user =
        signupWriter.completeSignup(expectedProvider, userUuid, request, details);
    return V2SocialAuthResponseDto.completed(user, tokenService.issueTokens(userUuid));
  }

  private V2SocialAuthResponseDto loginResponse(V2SocialLoginResult result) {
    if (result.signupStatus() == SignupStatus.PENDING) {
      V2SocialSignupToken signupToken = result.signupToken();
      return V2SocialAuthResponseDto.pending(signupToken.compactToken(), signupToken.expiresIn());
    }
    return V2SocialAuthResponseDto.completed(
        result.user(), tokenService.issueTokens(result.user().userUuid()));
  }
}
