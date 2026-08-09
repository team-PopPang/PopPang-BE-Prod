package com.poppang.be.domain.auth.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.auth.config.QaTokenProperties;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V2QaTokenService {

  private final QaTokenProperties properties;
  private final UsersRepository usersRepository;
  private final V2TokenService tokenService;

  public V2TokenResponseDto issueTokens(String account) {
    Role expectedRole = parseRole(account);
    String userUuid = properties.userUuid(expectedRole);
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.QA_ACCOUNT_NOT_READY));

    if (user.isDeleted()
        || user.getSignupStatus() != SignupStatus.COMPLETED
        || user.getRole() != expectedRole) {
      throw new BaseException(ErrorCode.QA_ACCOUNT_NOT_READY);
    }

    return tokenService.issueTokens(userUuid);
  }

  private Role parseRole(String account) {
    if (account == null || account.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_QA_ACCOUNT);
    }
    try {
      return Role.valueOf(account.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_QA_ACCOUNT);
    }
  }
}
