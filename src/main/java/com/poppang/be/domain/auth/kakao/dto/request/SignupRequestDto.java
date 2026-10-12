package com.poppang.be.domain.auth.kakao.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

@Getter
public class SignupRequestDto {

  private String uid;
  private Provider provider;
  private String email;
  private String nickname;
  private Role role;

  @JsonProperty("isAlerted")
  private boolean alerted;

  private String fcmToken;
  private List<String> alertKeywordList;
  private List<Long> recommendList;

  @Schema(
      description = "최초 가입 완료 기기의 OS. 생략 또는 null이면 미확인으로 저장합니다.",
      allowableValues = {"IOS", "ANDROID"},
      requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String signupOs;
}
