package com.poppang.be.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.redis.TokenHashRecord;
import com.poppang.be.domain.auth.redis.V2SignupTokenRedisRepository;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2SocialSignupWriterTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private UsersRepository usersRepository;
  @Mock private UserAlertKeywordRepository keywordRepository;
  @Mock private UserRecommendRepository userRecommendRepository;
  @Mock private RecommendRepository recommendRepository;
  @Mock private V2SignupTokenRedisRepository signupTokenRepository;

  @Test
  void completesAppleSignupUsingOnlyLockedUserAndServerTokenDetails() {
    Users pending = user(Provider.APPLE, SignupStatus.PENDING, false);
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(pending));
    given(usersRepository.existsByNickname("new-user")).willReturn(false);
    given(signupTokenRepository.consume(any(), any(TokenHashRecord.class))).willReturn(true);

    var response =
        writer()
            .completeSignup(
                Provider.APPLE,
                USER_UUID,
                new V2SignupRequestDto("new-user", true, "fcm-value", List.of(), List.of()),
                details());

    assertThat(response.userUuid()).isEqualTo(USER_UUID);
    assertThat(response.provider()).isEqualTo(Provider.APPLE);
    assertThat(response.role()).isEqualTo(Role.MEMBER);
    assertThat(pending.getNickname()).isEqualTo("new-user");
    assertThat(pending.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(pending.isAlerted()).isTrue();
    assertThat(pending.getFcmToken()).isEqualTo("fcm-value");
  }

  @Test
  void providerMismatchIsRejectedBeforeSignupTokenConsumption() {
    Users pending = user(Provider.GOOGLE, SignupStatus.PENDING, false);
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(pending));

    assertError(
        () ->
            writer()
                .completeSignup(
                    Provider.APPLE,
                    USER_UUID,
                    new V2SignupRequestDto("new-user", false, null, List.of(), List.of()),
                    details()),
        ErrorCode.SIGNUP_PROVIDER_MISMATCH);

    verify(signupTokenRepository, never()).consume(any(), any());
  }

  @Test
  void missingLatestSignupTokenDoesNotMutateProfile() {
    Users pending = user(Provider.GOOGLE, SignupStatus.PENDING, false);
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(pending));
    given(usersRepository.existsByNickname("new-user")).willReturn(false);
    given(signupTokenRepository.consume(any(), any(TokenHashRecord.class))).willReturn(false);

    assertError(
        () ->
            writer()
                .completeSignup(
                    Provider.GOOGLE,
                    USER_UUID,
                    new V2SignupRequestDto("new-user", true, "fcm-value", List.of(), List.of()),
                    details()),
        ErrorCode.SIGNUP_TOKEN_MISMATCH);

    assertThat(pending.getNickname()).isNull();
    assertThat(pending.getSignupStatus()).isEqualTo(SignupStatus.PENDING);
  }

  private V2SocialSignupWriter writer() {
    return new V2SocialSignupWriter(
        usersRepository,
        keywordRepository,
        userRecommendRepository,
        recommendRepository,
        signupTokenRepository);
  }

  private Users user(Provider provider, SignupStatus signupStatus, boolean deleted) {
    return Users.builder()
        .uuid(USER_UUID)
        .uid(provider.name().toLowerCase() + "-user")
        .provider(provider)
        .role(Role.MEMBER)
        .signupStatus(signupStatus)
        .deleted(deleted)
        .build();
  }

  private JwtAuthenticationDetails details() {
    return new JwtAuthenticationDetails(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "signup-jti",
        Instant.parse("2026-07-31T00:00:00Z"),
        Instant.parse("2026-07-31T00:15:00Z"));
  }

  private void assertError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }
}
