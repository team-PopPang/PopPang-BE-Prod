package com.poppang.be.domain.auth.application;

import com.poppang.be.domain.auth.dto.request.AutoLoginRequestDto;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UsersRepository usersRepository;

  @Override
  public LoginResponseDto autoLogin(AutoLoginRequestDto autoLoginRequestDto) {
    Users user =
        usersRepository
            .findByUuidAndDeletedFalse(autoLoginRequestDto.getUserUuid())
            .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. "));

    return LoginResponseDto.from(user);
  }
}
