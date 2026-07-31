package com.poppang.be.domain.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.dto.v2.request.V2ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateFcmTokenRequestDto;
import com.poppang.be.domain.users.dto.v2.response.V2NicknameDuplicateResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UpdateAlertStatusResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UserResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2UsersServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private UsersRepository usersRepository;
  @Mock private V2UsersDeactivationWriter deactivationWriter;
  @Mock private V2UserTokenCleanupService tokenCleanupService;

  private V2UsersServiceImpl usersService;

  @BeforeEach
  void setUp() {
    usersService = new V2UsersServiceImpl(usersRepository, deactivationWriter, tokenCleanupService);
  }

  @Test
  void getUserReturnsOnlyThePrincipalUsersSafeProfile() {
    Users user = completedUser();
    given(
            usersRepository.findByUuidAndDeletedFalseAndSignupStatus(
                USER_UUID, SignupStatus.COMPLETED))
        .willReturn(Optional.of(user));

    V2UserResponseDto response = usersService.getUser(USER_UUID);

    assertThat(response.userUuid()).isEqualTo(USER_UUID);
    assertThat(response.provider()).isEqualTo(Provider.KAKAO);
    assertThat(response.email()).isEqualTo("user@example.com");
    assertThat(response.nickname()).isEqualTo("팝팡");
    assertThat(response.role()).isEqualTo(Role.MEMBER);
    assertThat(response.alerted()).isTrue();
    assertThat(V2UserResponseDto.class.getRecordComponents())
        .extracting(component -> component.getName())
        .containsExactly("userUuid", "provider", "email", "nickname", "role", "alerted")
        .doesNotContain("uid", "fcmToken", "accessToken", "refreshToken");
  }

  @Test
  void alertStatusIsAppliedOnlyToThePrincipalUser() {
    Users user = completedUser();
    given(
            usersRepository.findByUuidAndDeletedFalseAndSignupStatus(
                USER_UUID, SignupStatus.COMPLETED))
        .willReturn(Optional.of(user));

    V2UpdateAlertStatusResponseDto response =
        usersService.updateAlertStatus(USER_UUID, new V2UpdateAlertStatusRequestDto(false));

    assertThat(user.isAlerted()).isFalse();
    assertThat(response.userUuid()).isEqualTo(USER_UUID);
    assertThat(response.alerted()).isFalse();
  }

  @Test
  void alertStatusRejectsMissingOrNullValues() {
    assertInvalidUserRequest(() -> usersService.updateAlertStatus(USER_UUID, null));
    assertInvalidUserRequest(
        () -> usersService.updateAlertStatus(USER_UUID, new V2UpdateAlertStatusRequestDto(null)));

    verify(usersRepository, never())
        .findByUuidAndDeletedFalseAndSignupStatus(USER_UUID, SignupStatus.COMPLETED);
  }

  @Test
  void nicknameDuplicateCheckTrimsTheValueAndKeepsLegacyOwnNicknameSemantics() {
    given(
            usersRepository.findByUuidAndDeletedFalseAndSignupStatus(
                USER_UUID, SignupStatus.COMPLETED))
        .willReturn(Optional.of(completedUser()));
    given(usersRepository.existsByNickname("팝팡")).willReturn(true);

    V2NicknameDuplicateResponseDto response =
        usersService.checkNicknameDuplicated(USER_UUID, "  팝팡  ");

    assertThat(response.duplicated()).isTrue();
    verify(usersRepository).existsByNickname("팝팡");
  }

  @Test
  void nicknameChangeTrimsAndStoresTheNormalizedValue() {
    Users user = completedUser();
    given(
            usersRepository.findByUuidAndDeletedFalseAndSignupStatus(
                USER_UUID, SignupStatus.COMPLETED))
        .willReturn(Optional.of(user));
    given(usersRepository.existsByNickname("새 닉네임")).willReturn(false);

    usersService.changeNickname(USER_UUID, new V2ChangeNicknameRequestDto("  새 닉네임  "));

    assertThat(user.getNickname()).isEqualTo("새 닉네임");
  }

  @Test
  void nicknameChangeRejectsTheCurrentNicknameAsDuplicateLikeV1() {
    given(
            usersRepository.findByUuidAndDeletedFalseAndSignupStatus(
                USER_UUID, SignupStatus.COMPLETED))
        .willReturn(Optional.of(completedUser()));
    given(usersRepository.existsByNickname("팝팡")).willReturn(true);

    assertThatThrownBy(
            () -> usersService.changeNickname(USER_UUID, new V2ChangeNicknameRequestDto("팝팡")))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_NICKNAME));
  }

  @Test
  void nicknameOperationsRejectNullAndBlankValues() {
    assertInvalidUserRequest(() -> usersService.checkNicknameDuplicated(USER_UUID, null));
    assertInvalidUserRequest(() -> usersService.checkNicknameDuplicated(USER_UUID, "  "));
    assertInvalidUserRequest(() -> usersService.changeNickname(USER_UUID, null));
    assertInvalidUserRequest(
        () -> usersService.changeNickname(USER_UUID, new V2ChangeNicknameRequestDto("  ")));
  }

  @Test
  void fcmTokenUpdateIsIdempotentAndNeverReturnsTheToken() {
    Users user = completedUser();
    given(
            usersRepository.findByUuidAndDeletedFalseAndSignupStatus(
                USER_UUID, SignupStatus.COMPLETED))
        .willReturn(Optional.of(user));
    V2UpdateFcmTokenRequestDto request = new V2UpdateFcmTokenRequestDto("new-fcm-token");

    usersService.updateFcmToken(USER_UUID, request);
    usersService.updateFcmToken(USER_UUID, request);

    assertThat(user.getFcmToken()).isEqualTo("new-fcm-token");
    verify(usersRepository, times(2))
        .findByUuidAndDeletedFalseAndSignupStatus(USER_UUID, SignupStatus.COMPLETED);
  }

  @Test
  void fcmTokenUpdateRejectsMissingOrBlankValues() {
    assertInvalidUserRequest(() -> usersService.updateFcmToken(USER_UUID, null));
    assertInvalidUserRequest(
        () -> usersService.updateFcmToken(USER_UUID, new V2UpdateFcmTokenRequestDto(null)));
    assertInvalidUserRequest(
        () -> usersService.updateFcmToken(USER_UUID, new V2UpdateFcmTokenRequestDto("  ")));
  }

  @Test
  void softDeleteCommitsThroughWriterBeforeCleaningAuthenticationStores() {
    usersService.softDelete(USER_UUID);

    InOrder order = inOrder(deactivationWriter, tokenCleanupService);
    order.verify(deactivationWriter).softDelete(USER_UUID);
    order.verify(tokenCleanupService).cleanup(USER_UUID);
  }

  @Test
  void missingActiveCompletedUserUsesTheExistingNotFoundError() {
    given(
            usersRepository.findByUuidAndDeletedFalseAndSignupStatus(
                USER_UUID, SignupStatus.COMPLETED))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> usersService.getUser(USER_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }

  private Users completedUser() {
    return Users.builder()
        .uid("provider-uid-must-not-leak")
        .uuid(USER_UUID)
        .provider(Provider.KAKAO)
        .email("user@example.com")
        .nickname("팝팡")
        .role(Role.MEMBER)
        .signupStatus(SignupStatus.COMPLETED)
        .alerted(true)
        .fcmToken("fcm-token-must-not-leak")
        .deleted(false)
        .build();
  }

  private void assertInvalidUserRequest(Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_USER_REQUEST));
  }
}
