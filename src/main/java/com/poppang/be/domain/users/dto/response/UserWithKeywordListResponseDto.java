package com.poppang.be.domain.users.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserWithKeywordListResponseDto {

  private Long userId;
  private String nickname;
  private String fcmToken;
  private String keyword;

  @Builder
  public UserWithKeywordListResponseDto(
      Long userId, String nickname, String fcmToken, String keyword) {
    this.userId = userId;
    this.nickname = nickname;
    this.fcmToken = fcmToken;
    this.keyword = keyword;
  }
}
