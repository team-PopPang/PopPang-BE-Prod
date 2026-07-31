package com.poppang.be.domain.auth.application;

import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.users.entity.SignupStatus;

record V2SocialLoginResult(
    SignupStatus signupStatus, V2AuthUserResponseDto user, V2SocialSignupToken signupToken) {

  static V2SocialLoginResult completed(V2AuthUserResponseDto user) {
    return new V2SocialLoginResult(SignupStatus.COMPLETED, user, null);
  }

  static V2SocialLoginResult pending(V2SocialSignupToken signupToken) {
    return new V2SocialLoginResult(SignupStatus.PENDING, null, signupToken);
  }
}
