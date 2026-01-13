package com.poppang.be.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TokenResponseDto {

  String tokenType;
  String accessToken;
  String refreshToken;

  @Builder
  public TokenResponseDto(String tokenType, String accessToken, String refreshToken) {
    this.tokenType = tokenType;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }
}
