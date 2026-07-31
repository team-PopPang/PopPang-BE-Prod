package com.poppang.be.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class V2SocialLoginWriterTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private UsersRepository usersRepository;
  @Mock private V2SocialSignupTokenService signupTokenService;

  @Test
  void completedGoogleUserReturnsProfileAndUsesVerifiedEmail() {
    Users user = user(Provider.GOOGLE, SignupStatus.COMPLETED, false, "old@example.com");
    given(usersRepository.findByProviderAndUid(Provider.GOOGLE, "google-user"))
        .willReturn(Optional.of(user));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));

    V2SocialLoginResult result =
        writer()
            .login(
                Provider.GOOGLE,
                new VerifiedSocialIdentity(Provider.GOOGLE, "google-user", "verified@example.com"));

    assertThat(result.signupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(result.user().provider()).isEqualTo(Provider.GOOGLE);
    assertThat(result.user().email()).isEqualTo("verified@example.com");
    verify(signupTokenService, never()).issue(any());
  }

  @Test
  void appleLoginWithoutRepeatedEmailPreservesTheStoredEmail() {
    Users user = user(Provider.APPLE, SignupStatus.COMPLETED, false, "stored@example.com");
    given(usersRepository.findByProviderAndUid(Provider.APPLE, "apple-user"))
        .willReturn(Optional.of(user));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));

    V2SocialLoginResult result =
        writer()
            .login(Provider.APPLE, new VerifiedSocialIdentity(Provider.APPLE, "apple-user", null));

    assertThat(result.user().email()).isEqualTo("stored@example.com");
    assertThat(user.getEmail()).isEqualTo("stored@example.com");
  }

  @Test
  void newAppleUserIsServerOwnedPendingMemberAndReceivesSignupToken() {
    AtomicReference<Users> saved = new AtomicReference<>();
    given(usersRepository.findByProviderAndUid(Provider.APPLE, "apple-user"))
        .willReturn(Optional.empty());
    given(usersRepository.existsByUidAndProviderNot("apple-user", Provider.APPLE))
        .willReturn(false);
    given(usersRepository.saveAndFlush(any(Users.class)))
        .willAnswer(
            invocation -> {
              Users user = invocation.getArgument(0);
              ReflectionTestUtils.setField(user, "uuid", USER_UUID);
              saved.set(user);
              return user;
            });
    given(usersRepository.findByUuidForUpdate(USER_UUID))
        .willAnswer(invocation -> Optional.ofNullable(saved.get()));
    given(signupTokenService.issue(USER_UUID))
        .willReturn(new V2SocialSignupToken("signup-token", 900));

    V2SocialLoginResult result =
        writer()
            .login(
                Provider.APPLE,
                new VerifiedSocialIdentity(Provider.APPLE, "apple-user", "verified@example.com"));

    assertThat(result.signupStatus()).isEqualTo(SignupStatus.PENDING);
    assertThat(result.signupToken().compactToken()).isEqualTo("signup-token");
    assertThat(saved.get().getProvider()).isEqualTo(Provider.APPLE);
    assertThat(saved.get().getRole()).isEqualTo(Role.MEMBER);
    assertThat(saved.get().getSignupStatus()).isEqualTo(SignupStatus.PENDING);
  }

  @Test
  void deletedUserAndCrossProviderIdentityAreRejected() {
    Users deleted = user(Provider.APPLE, SignupStatus.COMPLETED, true, null);
    given(usersRepository.findByProviderAndUid(Provider.APPLE, "apple-user"))
        .willReturn(Optional.of(deleted));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(deleted));

    assertError(
        () ->
            writer()
                .login(
                    Provider.APPLE, new VerifiedSocialIdentity(Provider.APPLE, "apple-user", null)),
        ErrorCode.ACCOUNT_NOT_ACTIVE);
    assertError(
        () ->
            writer()
                .login(
                    Provider.APPLE,
                    new VerifiedSocialIdentity(Provider.GOOGLE, "apple-user", null)),
        ErrorCode.SOCIAL_IDENTITY_CONFLICT);
  }

  @Test
  void differentProviderUsingSameLegacyUidIsRejectedBeforeInsert() {
    given(usersRepository.findByProviderAndUid(Provider.GOOGLE, "shared-uid"))
        .willReturn(Optional.empty());
    given(usersRepository.existsByUidAndProviderNot("shared-uid", Provider.GOOGLE))
        .willReturn(true);

    assertError(
        () ->
            writer()
                .login(
                    Provider.GOOGLE,
                    new VerifiedSocialIdentity(Provider.GOOGLE, "shared-uid", null)),
        ErrorCode.SOCIAL_IDENTITY_CONFLICT);

    verify(usersRepository, never()).saveAndFlush(any());
  }

  private V2SocialLoginWriter writer() {
    return new V2SocialLoginWriter(usersRepository, signupTokenService);
  }

  private Users user(Provider provider, SignupStatus signupStatus, boolean deleted, String email) {
    return Users.builder()
        .uuid(USER_UUID)
        .uid(provider == Provider.GOOGLE ? "google-user" : "apple-user")
        .provider(provider)
        .email(email)
        .role(Role.MEMBER)
        .signupStatus(signupStatus)
        .deleted(deleted)
        .build();
  }

  private void assertError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }
}
