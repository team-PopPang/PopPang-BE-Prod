package com.poppang.be.domain.users.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.Users;

public record V2UserResponseDto(
    String userUuid,
    Provider provider,
    String email,
    String nickname,
    Role role,
    @JsonProperty("isAlerted") boolean alerted) {

  public static V2UserResponseDto from(Users user) {
    return new V2UserResponseDto(
        user.getUuid(),
        user.getProvider(),
        user.getEmail(),
        user.getNickname(),
        user.getRole(),
        user.isAlerted());
  }
}
