package com.poppang.be.domain.auth.kakao.application;

import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
import com.poppang.be.domain.auth.kakao.dto.request.KakaoAppLoginRequestDto;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;

public interface KakaoAuthService {

  LoginResponseDto webLogin(String authCode);

  LoginResponseDto mobileLogin(KakaoAppLoginRequestDto kakaoAppLoginRequestDto);

  SignupResponseDto signup(SignupRequestDto signupRequestDto);
}
