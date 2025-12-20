package com.poppang.be.domain.keyword.dto.request;

import lombok.Getter;

@Getter
public class UserAlertKeywordRegisterRequestDto {

  private String userUuid;
  private String newAlertKeyword;
}
