package com.poppang.be.domain.users.dto.v2.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.poppang.be.domain.users.dto.request.ChangeNicknameRequestDto;

public final class V2ChangeNicknameRequestDto extends ChangeNicknameRequestDto {

  private final String nickname;

  @JsonCreator
  public V2ChangeNicknameRequestDto(@JsonProperty("nickname") String nickname) {
    this.nickname = nickname;
  }

  @Override
  public String getNickname() {
    return nickname;
  }
}
