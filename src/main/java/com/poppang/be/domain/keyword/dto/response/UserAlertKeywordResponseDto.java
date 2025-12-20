package com.poppang.be.domain.keyword.dto.response;

import com.poppang.be.domain.keyword.entity.UserAlertKeyword;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserAlertKeywordResponseDto {

  private String alertKeyword;

  @Builder
  public UserAlertKeywordResponseDto(String alertKeyword) {
    this.alertKeyword = alertKeyword;
  }

  public static UserAlertKeywordResponseDto from(UserAlertKeyword userAlertKeyword) {
    return UserAlertKeywordResponseDto.builder()
        .alertKeyword(userAlertKeyword.getAlertKeyword())
        .build();
  }
}
