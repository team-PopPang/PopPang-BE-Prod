package com.poppang.be.domain.users.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserWithKeywordListResponseDtoB {

  private Long userId;
  private String nickname;
  private String fcmToken;
  private List<String> keywordList;

  @Builder
  public UserWithKeywordListResponseDtoB(
      Long userId, String nickname, String fcmToken, List<String> keywordList) {
    this.userId = userId;
    this.nickname = nickname;
    this.fcmToken = fcmToken;
    this.keywordList = keywordList;
  }
}
