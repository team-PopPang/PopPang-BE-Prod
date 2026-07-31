package com.poppang.be.domain.auth.kakao.application;

import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.users.entity.SignupStatus;

record V2KakaoLoginResult(
    SignupStatus signupStatus, V2AuthUserResponseDto user, V2SignupToken signupToken) {

  static V2KakaoLoginResult completed(V2AuthUserResponseDto user) {
    return new V2KakaoLoginResult(SignupStatus.COMPLETED, user, null);
  }

  static V2KakaoLoginResult pending(V2SignupToken signupToken) {
    return new V2KakaoLoginResult(SignupStatus.PENDING, null, signupToken);
  }
}
