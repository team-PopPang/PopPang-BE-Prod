package com.poppang.be.domain.auth.apple.application;

import com.poppang.be.domain.auth.apple.dto.request.AppleAppLoginRequestDto;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;

public interface AppleAuthService {

  LoginResponseDto webLogin(String authCode);

  LoginResponseDto mobileLogin(AppleAppLoginRequestDto appleAppLoginRequestDto);

  SignupResponseDto signup(SignupRequestDto signupRequestDto);
}
