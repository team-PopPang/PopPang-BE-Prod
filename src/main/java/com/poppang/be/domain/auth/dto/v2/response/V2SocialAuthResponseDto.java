package com.poppang.be.domain.auth.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poppang.be.domain.users.entity.SignupStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record V2SocialAuthResponseDto(
    SignupStatus signupStatus,
    String tokenType,
    V2AuthUserResponseDto user,
    String accessToken,
    String refreshToken,
    Long accessTokenExpiresIn,
    Long refreshTokenExpiresIn,
    String signupToken,
    Long signupTokenExpiresIn) {

  public static V2SocialAuthResponseDto pending(String signupToken, long signupTokenExpiresIn) {
    return new V2SocialAuthResponseDto(
        SignupStatus.PENDING,
        "Bearer",
        null,
        null,
        null,
        null,
        null,
        signupToken,
        signupTokenExpiresIn);
  }

  public static V2SocialAuthResponseDto completed(
      V2AuthUserResponseDto user, V2TokenResponseDto tokens) {
    return new V2SocialAuthResponseDto(
        SignupStatus.COMPLETED,
        tokens.tokenType(),
        user,
        tokens.accessToken(),
        tokens.refreshToken(),
        tokens.accessTokenExpiresIn(),
        tokens.refreshTokenExpiresIn(),
        null,
        null);
  }
}
