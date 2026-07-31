package com.poppang.be.domain.auth.google.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.auth.google.config.GoogleProperties;
import com.poppang.be.domain.users.entity.Provider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestOperations;

class GoogleCredentialVerifierTest {

  private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
  private static final String WEB_CLIENT_ID = "google-web-client";
  private static final String IOS_CLIENT_ID = "google-ios-client";

  @Test
  void acceptsOnlyServerVerifiedGoogleIdentityAndVerifiedEmail() {
    GoogleCredentialVerifier verifier =
        verifier(
            credential ->
                token(
                    "https://accounts.google.com",
                    IOS_CLIENT_ID,
                    "google-user",
                    "verified@example.com",
                    true));

    VerifiedSocialIdentity identity = verifier.verifyMobileCredential("signed-id-token");

    assertThat(identity.provider()).isEqualTo(Provider.GOOGLE);
    assertThat(identity.uid()).isEqualTo("google-user");
    assertThat(identity.verifiedEmail()).isEqualTo("verified@example.com");
  }

  @Test
  void rejectsInvalidSignature() {
    GoogleCredentialVerifier verifier = verifier(credential -> null);

    assertInvalid(() -> verifier.verifyMobileCredential("forged-id-token"));
  }

  @Test
  void rejectsWrongIssuerAndAudienceEvenAfterTokenDecoderReturnsClaims() {
    GoogleCredentialVerifier wrongIssuer =
        verifier(
            credential ->
                token(
                    "https://attacker.example",
                    WEB_CLIENT_ID,
                    "google-user",
                    "verified@example.com",
                    true));
    GoogleCredentialVerifier wrongAudience =
        verifier(
            credential ->
                token(
                    "https://accounts.google.com",
                    "another-client",
                    "google-user",
                    "verified@example.com",
                    true));

    assertInvalid(() -> wrongIssuer.verifyMobileCredential("id-token"));
    assertInvalid(() -> wrongAudience.verifyMobileCredential("id-token"));
  }

  @Test
  void doesNotTrustUnverifiedEmailAndRejectsBlankCredential() {
    GoogleCredentialVerifier verifier =
        verifier(
            credential ->
                token(
                    "accounts.google.com",
                    WEB_CLIENT_ID,
                    "google-user",
                    "unverified@example.com",
                    false));

    assertThat(verifier.verifyMobileCredential("id-token").verifiedEmail()).isNull();
    assertInvalid(() -> verifier.verifyMobileCredential(" "));
  }

  private GoogleCredentialVerifier verifier(
      GoogleCredentialVerifier.GoogleIdTokenValidator validator) {
    GoogleProperties properties = new GoogleProperties();
    properties.setClientId(WEB_CLIENT_ID);
    properties.setIosClientId(IOS_CLIENT_ID);
    return new GoogleCredentialVerifier(
        properties, mock(RestOperations.class), validator, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private GoogleIdToken token(
      String issuer, String audience, String subject, String email, boolean emailVerified) {
    GoogleIdToken.Payload payload =
        new GoogleIdToken.Payload()
            .setIssuer(issuer)
            .setAudience(audience)
            .setSubject(subject)
            .setExpirationTimeSeconds(NOW.plusSeconds(600).getEpochSecond())
            .setEmail(email)
            .setEmailVerified(emailVerified);
    return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
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
