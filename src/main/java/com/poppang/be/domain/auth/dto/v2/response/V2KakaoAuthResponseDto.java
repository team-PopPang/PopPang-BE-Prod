package com.poppang.be.domain.auth.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poppang.be.domain.users.entity.SignupStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record V2KakaoAuthResponseDto(
    SignupStatus signupStatus,
    String tokenType,
    V2AuthUserResponseDto user,
    String accessToken,
    String refreshToken,
    Long accessTokenExpiresIn,
    Long refreshTokenExpiresIn,
    String signupToken,
    Long signupTokenExpiresIn) {

  public static V2KakaoAuthResponseDto pending(String signupToken, long signupTokenExpiresIn) {
    return new V2KakaoAuthResponseDto(
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

  public static V2KakaoAuthResponseDto completed(
      V2AuthUserResponseDto user, V2TokenResponseDto tokens) {
    return new V2KakaoAuthResponseDto(
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
