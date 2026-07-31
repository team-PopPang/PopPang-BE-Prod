package com.poppang.be.domain.users.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class V2UsersDeactivationWriter {

  private final UsersRepository usersRepository;

  @Transactional
  void softDelete(String userUuid) {
    Users user =
        usersRepository
            .findByUuidForUpdate(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    if (user.isDeleted()) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    user.softDelete();
  }
}
