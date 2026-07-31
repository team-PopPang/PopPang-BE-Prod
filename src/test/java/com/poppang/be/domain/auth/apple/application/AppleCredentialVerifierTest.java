package com.poppang.be.domain.auth.apple.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.JWTClaimsSet;
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtFingerprint;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import com.poppang.be.domain.auth.apple.dto.response.AppleTokenResponseDto;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.users.entity.Provider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestOperations;

class AppleCredentialVerifierTest {

  private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
  private static final String CLIENT_ID = "kr.co.poppang.PopPang";
  private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
  private static final String RAW_NONCE = "raw-nonce";
  private static final String HASHED_NONCE = JwtFingerprint.sha256(RAW_NONCE);

  @Test
  void exchangesOneTimeCodeAndAcceptsOnlyVerifiedAppleIdentity() {
    AppleCredentialVerifier verifier =
        verifier(
            claims(
                "https://appleid.apple.com",
                CLIENT_ID,
                "apple-user",
                "verified@example.com",
                true));

    VerifiedSocialIdentity identity =
        verifier.verifyMobileCredential("authorization-code", RAW_NONCE);

    assertThat(identity.provider()).isEqualTo(Provider.APPLE);
    assertThat(identity.uid()).isEqualTo("apple-user");
    assertThat(identity.verifiedEmail()).isEqualTo("verified@example.com");
  }

  @Test
  void rejectsInvalidSignature() {
    AppleCredentialVerifier verifier =
        verifier(
            (idToken, clientId) -> {
              throw new IllegalArgumentException("invalid signature");
            });

    assertInvalid(() -> verifier.verifyMobileCredential("authorization-code", RAW_NONCE));
  }

  @Test
  void rejectsWrongIssuerAndAudience() {
    AppleCredentialVerifier wrongIssuer =
        verifier(
            claims(
                "https://attacker.example", CLIENT_ID, "apple-user", "verified@example.com", true));
    AppleCredentialVerifier wrongAudience =
        verifier(
            claims(
                "https://appleid.apple.com",
                "another-client",
                "apple-user",
                "verified@example.com",
                true));

    assertInvalid(() -> wrongIssuer.verifyMobileCredential("authorization-code", RAW_NONCE));
    assertInvalid(() -> wrongAudience.verifyMobileCredential("authorization-code", RAW_NONCE));
  }

  @Test
  void ignoresUnverifiedEmailAndRejectsBlankCredential() {
    AppleCredentialVerifier verifier =
        verifier(
            claims(
                "https://appleid.apple.com",
                CLIENT_ID,
                "apple-user",
                "unverified@example.com",
                false));

    assertThat(verifier.verifyMobileCredential("authorization-code", RAW_NONCE).verifiedEmail())
        .isNull();
    assertInvalid(() -> verifier.verifyMobileCredential(" ", RAW_NONCE));
    assertInvalid(() -> verifier.verifyMobileCredential("authorization-code", " "));
    assertInvalid(() -> verifier.verifyMobileCredential("authorization-code"));
  }

  @Test
  void rejectsAppleIdTokenWithoutNonce() {
    AppleCredentialVerifier verifier =
        verifier(
            new JWTClaimsSet.Builder()
                .issuer("https://appleid.apple.com")
                .audience(CLIENT_ID)
                .subject("apple-user")
                .expirationTime(Date.from(NOW.plusSeconds(600)))
                .build());

    assertInvalid(() -> verifier.verifyMobileCredential("authorization-code", RAW_NONCE));
  }

  @Test
  void rejectsAppleIdTokenWithDifferentNonce() {
    AppleCredentialVerifier verifier =
        verifier(
            new JWTClaimsSet.Builder()
                .issuer("https://appleid.apple.com")
                .audience(CLIENT_ID)
                .subject("apple-user")
                .expirationTime(Date.from(NOW.plusSeconds(600)))
                .claim("nonce", JwtFingerprint.sha256("another-raw-nonce"))
                .build());

    assertInvalid(() -> verifier.verifyMobileCredential("authorization-code", RAW_NONCE));
  }

  private AppleCredentialVerifier verifier(JWTClaimsSet claims) {
    return verifier((idToken, clientId) -> claims);
  }

  private AppleCredentialVerifier verifier(
      AppleCredentialVerifier.AppleIdTokenValidator validator) {
    AppleProperties properties = new AppleProperties();
    properties.setClientId(CLIENT_ID);
    properties.setTokenUri(TOKEN_URI);
    properties.setRedirectUri("https://poppang.co.kr/api/v1/auth/apple/login");

    RestOperations restOperations = mock(RestOperations.class);
    when(restOperations.exchange(
            eq(TOKEN_URI),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AppleTokenResponseDto.class)))
        .thenReturn(ResponseEntity.ok(tokenResponse()));

    return new AppleCredentialVerifier(
        properties,
        restOperations,
        validator,
        () -> "generated-client-secret",
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private AppleTokenResponseDto tokenResponse() {
    AppleTokenResponseDto response = new AppleTokenResponseDto();
    ReflectionTestUtils.setField(response, "accessToken", "provider-access-token");
    ReflectionTestUtils.setField(response, "idToken", "signed-id-token");
    return response;
  }

  private JWTClaimsSet claims(
      String issuer, String audience, String subject, String email, boolean emailVerified) {
    return new JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(audience)
        .subject(subject)
        .expirationTime(Date.from(NOW.plusSeconds(600)))
        .claim("email", email)
        .claim("email_verified", emailVerified)
        .claim("nonce", HASHED_NONCE)
        .build();
  }

  private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SOCIAL_CREDENTIAL));
  }
}
