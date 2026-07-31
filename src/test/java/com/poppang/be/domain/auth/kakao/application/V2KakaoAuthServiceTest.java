package com.poppang.be.domain.auth.kakao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.application.V2TokenService;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

class V2KakaoAuthServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private final KakaoCredentialVerifier verifier = Mockito.mock(KakaoCredentialVerifier.class);
  private final V2KakaoLoginWriter loginWriter = Mockito.mock(V2KakaoLoginWriter.class);
  private final V2KakaoSignupWriter signupWriter = Mockito.mock(V2KakaoSignupWriter.class);
  private final V2TokenService tokenService = Mockito.mock(V2TokenService.class);
  private final V2KakaoAuthService service =
      new V2KakaoAuthService(verifier, loginWriter, signupWriter, tokenService);

  @Test
  void completedLoginReturnsOnlyProfileAndAccessRefreshPair() {
    VerifiedSocialIdentity identity =
        new VerifiedSocialIdentity(Provider.KAKAO, "provider-user", "mail@example.com");
    V2AuthUserResponseDto user = user();
    given(verifier.verifyMobileCredential("provider-token")).willReturn(identity);
    given(loginWriter.login(identity)).willReturn(V2KakaoLoginResult.completed(user));
    given(tokenService.issueTokens(USER_UUID)).willReturn(tokens());

    var response = service.mobileLogin("provider-token");

    assertThat(response.signupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(response.user()).isEqualTo(user);
    assertThat(response.accessToken()).isEqualTo("access");
    assertThat(response.refreshToken()).isEqualTo("refresh");
    assertThat(response.signupToken()).isNull();
  }

  @Test
  void pendingLoginReturnsOnlyLatestSignupTokenAndPropertyExpiry() {
    VerifiedSocialIdentity identity =
        new VerifiedSocialIdentity(Provider.KAKAO, "provider-user", null);
    given(verifier.verifyMobileCredential("provider-token")).willReturn(identity);
    given(loginWriter.login(identity))
        .willReturn(V2KakaoLoginResult.pending(new V2SignupToken("signup", 1020)));

    var response = service.mobileLogin("provider-token");

    assertThat(response.signupStatus()).isEqualTo(SignupStatus.PENDING);
    assertThat(response.signupToken()).isEqualTo("signup");
    assertThat(response.signupTokenExpiresIn()).isEqualTo(1020);
    assertThat(response.accessToken()).isNull();
    assertThat(response.refreshToken()).isNull();
    verify(tokenService, Mockito.never()).issueTokens(Mockito.any());
  }

  @Test
  void uniqueCreateCollisionRetriesOnlyThroughExactIdentityRecovery() {
    VerifiedSocialIdentity identity =
        new VerifiedSocialIdentity(Provider.KAKAO, "provider-user", null);
    V2KakaoLoginResult recovered =
        V2KakaoLoginResult.pending(new V2SignupToken("replacement", 900));
    given(verifier.verifyMobileCredential("provider-token")).willReturn(identity);
    given(loginWriter.login(identity))
        .willThrow(new DataIntegrityViolationException("unique collision"));
    given(loginWriter.recoverAfterCreateCollision(identity)).willReturn(recovered);

    var response = service.mobileLogin("provider-token");

    assertThat(response.signupToken()).isEqualTo("replacement");
    verify(loginWriter).recoverAfterCreateCollision(identity);
  }

  @Test
  void signupIssuesAccessRefreshOnlyAfterTransactionalWriterReturns() {
    V2SignupRequestDto request =
        new V2SignupRequestDto("nickname", true, "fcm", List.of(), List.of());
    JwtAuthenticationDetails details = details();
    given(signupWriter.completeSignup(USER_UUID, request, details)).willReturn(user());
    given(tokenService.issueTokens(USER_UUID)).willReturn(tokens());

    var response = service.signup(USER_UUID, request, details);

    InOrder order = inOrder(signupWriter, tokenService);
    order.verify(signupWriter).completeSignup(USER_UUID, request, details);
    order.verify(tokenService).issueTokens(USER_UUID);
    assertThat(response.signupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(response.accessTokenExpiresIn()).isEqualTo(900);
    assertThat(response.refreshTokenExpiresIn()).isEqualTo(2_592_000);
  }

  @Test
  void orchestrationAndTransactionalWritersRemainSeparateProxyBoundaries() throws Exception {
    assertThat(
            V2KakaoAuthService.class
                .getMethod(
                    "signup",
                    String.class,
                    V2SignupRequestDto.class,
                    JwtAuthenticationDetails.class)
                .isAnnotationPresent(Transactional.class))
        .isFalse();
    assertThat(
            V2KakaoLoginWriter.class
                .getDeclaredMethod("login", VerifiedSocialIdentity.class)
                .isAnnotationPresent(Transactional.class))
        .isTrue();
    assertThat(
            V2KakaoSignupWriter.class
                .getDeclaredMethod(
                    "completeSignup",
                    String.class,
                    V2SignupRequestDto.class,
                    JwtAuthenticationDetails.class)
                .isAnnotationPresent(Transactional.class))
        .isTrue();
  }

  private V2AuthUserResponseDto user() {
    return new V2AuthUserResponseDto(
        USER_UUID, Provider.KAKAO, "mail@example.com", "nickname", Role.MEMBER, true);
  }

  private V2TokenResponseDto tokens() {
    return new V2TokenResponseDto("Bearer", "access", "refresh", 900, 2_592_000);
  }

  private JwtAuthenticationDetails details() {
    return new JwtAuthenticationDetails(
        "a".repeat(64),
        "jwt-id",
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"));
  }
}
