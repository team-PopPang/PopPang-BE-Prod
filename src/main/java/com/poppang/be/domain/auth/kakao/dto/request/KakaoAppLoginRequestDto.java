package com.poppang.be.domain.auth.kakao.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoAppLoginRequestDto {

  @JsonProperty("access_token")
  private String accessToken;
}
