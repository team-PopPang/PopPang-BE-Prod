package com.poppang.be.domain.auth.kakao.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poppang.be.domain.users.entity.Users;
import java.util.Map;
import lombok.Getter;

@Getter
public class KakaoUserInfoResponseDto {

  private Long id;

  @JsonProperty("kakao_account")
  private Map<String, Object> kakaoAccount;

  public Users toEntity() {
    return Users.builder().uid(String.valueOf(id)).build();
  }
}
