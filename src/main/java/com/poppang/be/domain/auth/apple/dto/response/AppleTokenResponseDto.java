package com.poppang.be.domain.auth.apple.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AppleTokenResponseDto {

  @JsonProperty("id_token")
  private String idToken;

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("expires_in")
  private String expiresIn;

  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("token_type")
  private String tokenType;
}
