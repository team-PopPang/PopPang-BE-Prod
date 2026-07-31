package com.poppang.be.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class V2SocialAuthServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private ProviderCredentialVerifier verifier;
  @Mock private V2SocialLoginWriter loginWriter;
  @Mock private V2SocialSignupWriter signupWriter;
  @Mock private V2TokenService tokenService;

  @Test
  void completedGoogleLoginReturnsAccessAndRefreshTokens() {
    VerifiedSocialIdentity identity =
        new VerifiedSocialIdentity(Provider.GOOGLE, "google-user", "verified@example.com");
    given(verifier.verifyMobileCredential("id-token")).willReturn(identity);
    given(loginWriter.login(Provider.GOOGLE, identity))
        .willReturn(V2SocialLoginResult.completed(user(Provider.GOOGLE)));
    given(tokenService.issueTokens(USER_UUID)).willReturn(tokens());

    var response = service().mobileLogin(Provider.GOOGLE, verifier, "id-token");

    assertThat(response.signupStatus().name()).isEqualTo("COMPLETED");
    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    assertThat(response.signupToken()).isNull();
  }

  @Test
  void pendingAppleLoginReturnsOnlySignupToken() {
    VerifiedSocialIdentity identity =
        new VerifiedSocialIdentity(Provider.APPLE, "apple-user", null);
    given(verifier.verifyMobileCredential("authorization-code", "raw-nonce")).willReturn(identity);
    given(loginWriter.login(Provider.APPLE, identity))
        .willReturn(V2SocialLoginResult.pending(new V2SocialSignupToken("signup-token", 900)));

    var response =
        service().mobileLogin(Provider.APPLE, verifier, "authorization-code", "raw-nonce");

    assertThat(response.signupStatus().name()).isEqualTo("PENDING");
    assertThat(response.signupToken()).isEqualTo("signup-token");
    assertThat(response.accessToken()).isNull();
    verify(tokenService, never()).issueTokens(USER_UUID);
  }

  @Test
  void concurrentCreateCollisionRecoversOnlyTheSameProviderIdentity() {
    VerifiedSocialIdentity identity =
        new VerifiedSocialIdentity(Provider.GOOGLE, "google-user", null);
    given(verifier.verifyMobileCredential("id-token")).willReturn(identity);
    given(loginWriter.login(Provider.GOOGLE, identity))
        .willThrow(new DataIntegrityViolationException("duplicate"));
    given(loginWriter.recoverAfterCreateCollision(Provider.GOOGLE, identity))
        .willReturn(V2SocialLoginResult.pending(new V2SocialSignupToken("replacement", 900)));

    var response = service().mobileLogin(Provider.GOOGLE, verifier, "id-token");

    assertThat(response.signupToken()).isEqualTo("replacement");
    verify(loginWriter).recoverAfterCreateCollision(Provider.GOOGLE, identity);
  }

  @Test
  void verifierCannotSwitchTheExpectedProvider() {
    given(verifier.verifyMobileCredential("credential"))
        .willReturn(new VerifiedSocialIdentity(Provider.APPLE, "apple-user", null));

    assertThatThrownBy(() -> service().mobileLogin(Provider.GOOGLE, verifier, "credential"))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_IDENTITY_CONFLICT));
    verify(loginWriter, never())
        .login(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void signupIssuesTokensOnlyAfterTransactionalWriterReturns() {
    V2SignupRequestDto request =
        new V2SignupRequestDto("nickname", false, null, List.of(), List.of());
    JwtAuthenticationDetails details =
        new JwtAuthenticationDetails(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "jwt-id",
            Instant.parse("2026-07-31T00:00:00Z"),
            Instant.parse("2026-07-31T00:15:00Z"));
    given(signupWriter.completeSignup(Provider.APPLE, USER_UUID, request, details))
        .willReturn(user(Provider.APPLE));
    given(tokenService.issueTokens(USER_UUID)).willReturn(tokens());

    var response = service().signup(Provider.APPLE, USER_UUID, request, details);

    InOrder order = inOrder(signupWriter, tokenService);
    order.verify(signupWriter).completeSignup(Provider.APPLE, USER_UUID, request, details);
    order.verify(tokenService).issueTokens(USER_UUID);
    assertThat(response.user().provider()).isEqualTo(Provider.APPLE);
    assertThat(response.accessToken()).isEqualTo("access-token");
  }

  private V2SocialAuthService service() {
    return new V2SocialAuthService(loginWriter, signupWriter, tokenService);
  }

  private V2AuthUserResponseDto user(Provider provider) {
    return new V2AuthUserResponseDto(
        USER_UUID, provider, "verified@example.com", "nickname", Role.MEMBER, false);
  }

  private V2TokenResponseDto tokens() {
    return new V2TokenResponseDto("Bearer", "access-token", "refresh-token", 900, 2_592_000);
  }
}
