package com.poppang.be.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.auth.config.QaTokenProperties;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2QaTokenServiceTest {

  private static final String API_KEY = "qa-api-key-0123456789-abcdefghijklmnop";
  private static final String MEMBER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String ADMIN_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private UsersRepository usersRepository;
  @Mock private V2TokenService tokenService;

  private V2QaTokenService qaTokenService;

  @BeforeEach
  void setUp() {
    qaTokenService =
        new V2QaTokenService(
            new QaTokenProperties(API_KEY, MEMBER_UUID, ADMIN_UUID), usersRepository, tokenService);
  }

  @Test
  void issuesTokensForTheConfiguredMemberAccountCaseInsensitively() {
    V2TokenResponseDto tokens = tokens("member-access", "member-refresh");
    given(usersRepository.findByUuid(MEMBER_UUID))
        .willReturn(Optional.of(user(MEMBER_UUID, Role.MEMBER, SignupStatus.COMPLETED, false)));
    given(tokenService.issueTokens(MEMBER_UUID)).willReturn(tokens);

    assertThat(qaTokenService.issueTokens(" member ")).isSameAs(tokens);

    verify(tokenService).issueTokens(MEMBER_UUID);
  }

  @Test
  void issuesTokensForTheConfiguredAdminAccount() {
    V2TokenResponseDto tokens = tokens("admin-access", "admin-refresh");
    given(usersRepository.findByUuid(ADMIN_UUID))
        .willReturn(Optional.of(user(ADMIN_UUID, Role.ADMIN, SignupStatus.COMPLETED, false)));
    given(tokenService.issueTokens(ADMIN_UUID)).willReturn(tokens);

    assertThat(qaTokenService.issueTokens("ADMIN")).isSameAs(tokens);

    verify(tokenService).issueTokens(ADMIN_UUID);
  }

  @Test
  void rejectsMissingBlankAndUnsupportedAccountValuesBeforeDatabaseAccess() {
    assertError(() -> qaTokenService.issueTokens(null), ErrorCode.INVALID_QA_ACCOUNT);
    assertError(() -> qaTokenService.issueTokens("  "), ErrorCode.INVALID_QA_ACCOUNT);
    assertError(() -> qaTokenService.issueTokens("OWNER"), ErrorCode.INVALID_QA_ACCOUNT);

    verifyNoInteractions(usersRepository, tokenService);
  }

  @Test
  void rejectsMissingDeletedPendingAndRoleMismatchedConfiguredAccounts() {
    given(usersRepository.findByUuid(MEMBER_UUID)).willReturn(Optional.empty());
    assertError(() -> qaTokenService.issueTokens("MEMBER"), ErrorCode.QA_ACCOUNT_NOT_READY);

    given(usersRepository.findByUuid(MEMBER_UUID))
        .willReturn(Optional.of(user(MEMBER_UUID, Role.MEMBER, SignupStatus.COMPLETED, true)));
    assertError(() -> qaTokenService.issueTokens("MEMBER"), ErrorCode.QA_ACCOUNT_NOT_READY);

    given(usersRepository.findByUuid(MEMBER_UUID))
        .willReturn(Optional.of(user(MEMBER_UUID, Role.MEMBER, SignupStatus.PENDING, false)));
    assertError(() -> qaTokenService.issueTokens("MEMBER"), ErrorCode.QA_ACCOUNT_NOT_READY);

    given(usersRepository.findByUuid(MEMBER_UUID))
        .willReturn(Optional.of(user(MEMBER_UUID, Role.ADMIN, SignupStatus.COMPLETED, false)));
    assertError(() -> qaTokenService.issueTokens("MEMBER"), ErrorCode.QA_ACCOUNT_NOT_READY);

    verify(tokenService, never()).issueTokens(MEMBER_UUID);
  }

  private Users user(String uuid, Role role, SignupStatus signupStatus, boolean deleted) {
    return Users.builder()
        .uuid(uuid)
        .role(role)
        .signupStatus(signupStatus)
        .deleted(deleted)
        .build();
  }

  private V2TokenResponseDto tokens(String accessToken, String refreshToken) {
    return new V2TokenResponseDto("Bearer", accessToken, refreshToken, 900, 2_592_000);
  }

  private void assertError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }
}
