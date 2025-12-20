package com.poppang.be.domain.auth.apple.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AppleUserInfoResponseDto {

  private String sub;
  private String uid;
  private String email;

  public AppleUserInfoResponseDto(String sub, String uid, String email) {
    this.sub = sub;
    this.uid = uid;
    this.email = email;
  }
}
