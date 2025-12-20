package com.poppang.be.domain.auth.google.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleAppLoginRequestDto {

  @JsonProperty("id_token")
  private String idToken;
}
