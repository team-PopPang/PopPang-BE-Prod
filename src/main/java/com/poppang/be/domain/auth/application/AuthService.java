package com.poppang.be.domain.auth.application;

import com.poppang.be.domain.auth.dto.request.AutoLoginRequestDto;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;

public interface AuthService {

  LoginResponseDto autoLogin(AutoLoginRequestDto autoLoginRequestDto);
}
