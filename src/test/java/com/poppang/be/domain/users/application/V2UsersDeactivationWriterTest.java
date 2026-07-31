package com.poppang.be.domain.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2UsersDeactivationWriterTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private UsersRepository usersRepository;

  @Test
  void softDeleteKeepsTheUsersRowAndOnlyMarksItDeleted() {
    Users user =
        Users.builder()
            .id(1L)
            .uuid(USER_UUID)
            .signupStatus(SignupStatus.COMPLETED)
            .deleted(false)
            .build();
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));
    V2UsersDeactivationWriter writer = new V2UsersDeactivationWriter(usersRepository);

    writer.softDelete(USER_UUID);

    assertThat(user.isDeleted()).isTrue();
    verify(usersRepository, never()).delete(user);
    verify(usersRepository, never()).deleteById(1L);
  }

  @Test
  void alreadyDeletedUserIsRejectedWithoutPhysicalDeletion() {
    Users user =
        Users.builder()
            .id(1L)
            .uuid(USER_UUID)
            .signupStatus(SignupStatus.COMPLETED)
            .deleted(true)
            .build();
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));
    V2UsersDeactivationWriter writer = new V2UsersDeactivationWriter(usersRepository);

    assertThatThrownBy(() -> writer.softDelete(USER_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE));

    verify(usersRepository, never()).delete(user);
  }
}
