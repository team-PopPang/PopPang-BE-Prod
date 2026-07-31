package com.poppang.be.domain.auth.kakao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class V2KakaoLoginWriterTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String UID = "provider-user";

  private final UsersRepository usersRepository = Mockito.mock(UsersRepository.class);
  private final V2SignupTokenService signupTokenService = Mockito.mock(V2SignupTokenService.class);
  private final V2KakaoLoginWriter writer =
      new V2KakaoLoginWriter(usersRepository, signupTokenService);

  @Test
  void completedUserGetsVerifiedEmailAndDoesNotReceiveSignupToken() {
    Users user = user(SignupStatus.COMPLETED, false, "old@example.com");
    given(usersRepository.findByProviderAndUid(Provider.KAKAO, UID)).willReturn(Optional.of(user));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));

    V2KakaoLoginResult result =
        writer.login(new VerifiedSocialIdentity(Provider.KAKAO, UID, "new@example.com"));

    assertThat(result.signupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(result.user().email()).isEqualTo("new@example.com");
    assertThat(result.signupToken()).isNull();
    verify(signupTokenService, never()).issue(any());
  }

  @Test
  void pendingUserGetsLatestSignupTokenAfterWriteLock() {
    Users user = user(SignupStatus.PENDING, false, null);
    V2SignupToken token = new V2SignupToken("signup.token", 900);
    given(usersRepository.findByProviderAndUid(Provider.KAKAO, UID)).willReturn(Optional.of(user));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));
    given(signupTokenService.issue(USER_UUID)).willReturn(token);

    V2KakaoLoginResult result = writer.login(new VerifiedSocialIdentity(Provider.KAKAO, UID, null));

    assertThat(result.signupStatus()).isEqualTo(SignupStatus.PENDING);
    assertThat(result.signupToken()).isEqualTo(token);
    verify(usersRepository).findByUuidForUpdate(USER_UUID);
  }

  @Test
  void newUserIsCreatedAsKakaoMemberPendingWithOnlyVerifiedEmail() {
    given(usersRepository.findByProviderAndUid(Provider.KAKAO, UID)).willReturn(Optional.empty());
    given(usersRepository.existsByUidAndProviderNot(UID, Provider.KAKAO)).willReturn(false);
    given(usersRepository.saveAndFlush(any(Users.class)))
        .willAnswer(
            invocation -> {
              Users saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "uuid", USER_UUID);
              return saved;
            });
    given(usersRepository.findByUuidForUpdate(USER_UUID))
        .willAnswer(
            invocation ->
                Optional.of(
                    Users.builder()
                        .uid(UID)
                        .uuid(USER_UUID)
                        .provider(Provider.KAKAO)
                        .email("verified@example.com")
                        .role(Role.MEMBER)
                        .signupStatus(SignupStatus.PENDING)
                        .build()));
    given(signupTokenService.issue(USER_UUID)).willReturn(new V2SignupToken("signup.token", 900));

    V2KakaoLoginResult result =
        writer.login(new VerifiedSocialIdentity(Provider.KAKAO, UID, "verified@example.com"));

    org.mockito.ArgumentCaptor<Users> created = org.mockito.ArgumentCaptor.forClass(Users.class);
    verify(usersRepository).saveAndFlush(created.capture());
    assertThat(created.getValue().getProvider()).isEqualTo(Provider.KAKAO);
    assertThat(created.getValue().getRole()).isEqualTo(Role.MEMBER);
    assertThat(created.getValue().getSignupStatus()).isEqualTo(SignupStatus.PENDING);
    assertThat(created.getValue().getEmail()).isEqualTo("verified@example.com");
    assertThat(result.signupStatus()).isEqualTo(SignupStatus.PENDING);
  }

  @Test
  void deletedUserCannotLogin() {
    Users user = user(SignupStatus.COMPLETED, true, null);
    given(usersRepository.findByProviderAndUid(Provider.KAKAO, UID)).willReturn(Optional.of(user));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> writer.login(new VerifiedSocialIdentity(Provider.KAKAO, UID, null)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE);
  }

  @Test
  void differentProviderWithSameUidIsAnExplicitConflict() {
    given(usersRepository.findByProviderAndUid(Provider.KAKAO, UID)).willReturn(Optional.empty());
    given(usersRepository.existsByUidAndProviderNot(UID, Provider.KAKAO)).willReturn(true);

    assertThatThrownBy(() -> writer.login(new VerifiedSocialIdentity(Provider.KAKAO, UID, null)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
  }

  @Test
  void collisionRecoveryReusesOnlyTheExactProviderIdentity() {
    Users user = user(SignupStatus.PENDING, false, null);
    given(usersRepository.findByProviderAndUid(Provider.KAKAO, UID)).willReturn(Optional.of(user));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));
    given(signupTokenService.issue(USER_UUID)).willReturn(new V2SignupToken("replacement", 900));

    V2KakaoLoginResult result =
        writer.recoverAfterCreateCollision(new VerifiedSocialIdentity(Provider.KAKAO, UID, null));

    assertThat(result.signupStatus()).isEqualTo(SignupStatus.PENDING);
  }

  private Users user(SignupStatus status, boolean deleted, String email) {
    return Users.builder()
        .uid(UID)
        .uuid(USER_UUID)
        .provider(Provider.KAKAO)
        .email(email)
        .role(Role.MEMBER)
        .signupStatus(status)
        .deleted(deleted)
        .build();
  }
}
