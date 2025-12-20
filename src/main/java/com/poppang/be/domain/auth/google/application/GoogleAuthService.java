package com.poppang.be.domain.auth.google.application;

import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
import com.poppang.be.domain.auth.google.dto.request.GoogleAppLoginRequestDto;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;

public interface GoogleAuthService {

  LoginResponseDto webLogin(String authCode);

  LoginResponseDto mobileLogin(GoogleAppLoginRequestDto googleAppLoginRequestDto);

  SignupResponseDto signup(SignupRequestDto signupRequestDto);
}
