package com.poppang.be.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private static final String SECRET =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String OTHER_SECRET =
      "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
  private static final String USER_UUID = "00000000-0000-0000-0000-000000000001";
  private static final String ISSUER = "poppang";
  private static final String APP_AUDIENCE = "poppang-app-v2";
  private static final String SIGNUP_AUDIENCE = "poppang-signup-v2";
  private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");

  private JwtProperties properties;
  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    properties = new JwtProperties(SECRET, 15, 30, ISSUER, APP_AUDIENCE, SIGNUP_AUDIENCE, 15);
    jwtProvider = new JwtProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createsAndVerifiesAccessAndRefreshTokensWithOneSession() {
    String sessionId = UUID.randomUUID().toString();

    VerifiedJwt access = jwtProvider.verify(jwtProvider.createAccessToken(USER_UUID, sessionId));
    VerifiedJwt refresh = jwtProvider.verify(jwtProvider.createRefreshToken(USER_UUID, sessionId));

    assertThat(access.userUuid()).isEqualTo(USER_UUID);
    assertThat(access.tokenType()).isEqualTo(JwtTokenType.ACCESS);
    assertThat(access.audience()).isEqualTo(APP_AUDIENCE);
    assertThat(access.issuedAt()).isEqualTo(NOW);
    assertThat(access.expiresAt()).isEqualTo(NOW.plus(15, ChronoUnit.MINUTES));
    assertThat(access.sessionId()).isEqualTo(sessionId);
    assertThat(access.jwtId()).isNotBlank();
    assertThat(access.toString())
        .doesNotContain(access.jwtId(), access.sessionId())
        .contains("[REDACTED]");

    assertThat(refresh.userUuid()).isEqualTo(USER_UUID);
    assertThat(refresh.tokenType()).isEqualTo(JwtTokenType.REFRESH);
    assertThat(refresh.audience()).isEqualTo(APP_AUDIENCE);
    assertThat(refresh.issuedAt()).isEqualTo(NOW);
    assertThat(refresh.expiresAt()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
    assertThat(refresh.sessionId()).isEqualTo(sessionId);
    assertThat(refresh.jwtId()).isNotBlank().isNotEqualTo(access.jwtId());
  }

  @Test
  void createsAndVerifiesSignupTokenWithoutSession() {
    VerifiedJwt signup = jwtProvider.verify(jwtProvider.createSignupToken(USER_UUID));

    assertThat(signup.userUuid()).isEqualTo(USER_UUID);
    assertThat(signup.tokenType()).isEqualTo(JwtTokenType.SIGNUP);
    assertThat(signup.audience()).isEqualTo(SIGNUP_AUDIENCE);
    assertThat(signup.issuedAt()).isEqualTo(NOW);
    assertThat(signup.expiresAt()).isEqualTo(NOW.plus(15, ChronoUnit.MINUTES));
    assertThat(signup.jwtId()).isNotBlank();
    assertThat(signup.sessionId()).isNull();
  }

  @Test
  void jtiMakesTokensUniqueWhenIssuedAtTheSameInstant() {
    String sessionId = UUID.randomUUID().toString();

    String first = jwtProvider.createAccessToken(USER_UUID, sessionId);
    String second = jwtProvider.createAccessToken(USER_UUID, sessionId);

    assertThat(second).isNotEqualTo(first);
    assertThat(jwtProvider.verify(second).jwtId()).isNotEqualTo(jwtProvider.verify(first).jwtId());
  }

  @Test
  void createsUniqueUuidSessionIds() {
    String first = jwtProvider.createSessionId();
    String second = jwtProvider.createSessionId();

    assertThat(UUID.fromString(first).toString()).isEqualTo(first);
    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void rejectsWrongAudienceAndUnknownType() {
    String wrongAudience = signedToken("ACCESS", SIGNUP_AUDIENCE, UUID.randomUUID().toString());
    String unknownType = signedToken("UNKNOWN", APP_AUDIENCE, UUID.randomUUID().toString());

    assertError(wrongAudience, ErrorCode.INVALID_TOKEN);
    assertError(unknownType, ErrorCode.UNSUPPORTED_TOKEN);
  }

  @Test
  void rejectsWrongIssuerAndMalformedToken() {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String wrongIssuer =
        requiredClaimsBuilder("ACCESS", APP_AUDIENCE)
            .issuer("another-issuer")
            .claim("sid", UUID.randomUUID().toString())
            .signWith(key, Jwts.SIG.HS256)
            .compact();

    assertError(wrongIssuer, ErrorCode.INVALID_TOKEN);
    assertError("not-a-jwt", ErrorCode.MALFORMED_TOKEN);
  }

  @Test
  void rejectsMissingRequiredClaimsAndInvalidSessionShape() {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String missingSession =
        requiredClaimsBuilder("ACCESS", APP_AUDIENCE).signWith(key, Jwts.SIG.HS256).compact();
    String signupWithSession =
        requiredClaimsBuilder("SIGNUP", SIGNUP_AUDIENCE)
            .claim("sid", UUID.randomUUID().toString())
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    String missingJwtId =
        Jwts.builder()
            .subject(USER_UUID)
            .issuer(ISSUER)
            .audience()
            .add(APP_AUDIENCE)
            .and()
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plus(15, ChronoUnit.MINUTES)))
            .claim("typ", "ACCESS")
            .claim("sid", UUID.randomUUID().toString())
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    String missingIssuedAt =
        Jwts.builder()
            .subject(USER_UUID)
            .issuer(ISSUER)
            .audience()
            .add(APP_AUDIENCE)
            .and()
            .expiration(Date.from(NOW.plus(15, ChronoUnit.MINUTES)))
            .id(UUID.randomUUID().toString())
            .claim("typ", "ACCESS")
            .claim("sid", UUID.randomUUID().toString())
            .signWith(key, Jwts.SIG.HS256)
            .compact();

    assertError(missingSession, ErrorCode.INVALID_TOKEN);
    assertError(signupWithSession, ErrorCode.INVALID_TOKEN);
    assertError(missingJwtId, ErrorCode.INVALID_TOKEN);
    assertError(missingIssuedAt, ErrorCode.INVALID_TOKEN);
  }

  @Test
  void rejectsNonHs256AndUnsecuredTokens() {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    String hs512 =
        baseBuilder("ACCESS", APP_AUDIENCE, UUID.randomUUID().toString())
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    String unsecured = baseBuilder("ACCESS", APP_AUDIENCE, UUID.randomUUID().toString()).compact();

    assertError(hs512, ErrorCode.UNSUPPORTED_TOKEN);
    assertError(unsecured, ErrorCode.UNSUPPORTED_TOKEN);
  }

  @Test
  void rejectsInvalidSignatureAndExpiredToken() {
    SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
    String invalidSignature =
        baseBuilder("ACCESS", APP_AUDIENCE, UUID.randomUUID().toString())
            .signWith(otherKey, Jwts.SIG.HS256)
            .compact();
    String access = jwtProvider.createAccessToken(USER_UUID, UUID.randomUUID().toString());
    JwtProvider afterExpiration =
        new JwtProvider(properties, Clock.fixed(NOW.plus(16, ChronoUnit.MINUTES), ZoneOffset.UTC));

    assertError(invalidSignature, ErrorCode.TOKEN_SIGNATURE_INVALID);
    assertThatThrownBy(() -> afterExpiration.verify(access))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXPIRED_TOKEN));
  }

  @Test
  void keepsLegacyTokenMethodsWorkingButDoesNotAcceptLegacyTokenAsV2() {
    String legacyAccess = jwtProvider.createAccessToken(USER_UUID);

    assertThat(jwtProvider.getUserUuid(legacyAccess)).isEqualTo(USER_UUID);
    assertThat(jwtProvider.getTokenType(legacyAccess)).isEqualTo(JwtTokenType.ACCESS);
    jwtProvider.assertAccessToken(legacyAccess);
    assertError(legacyAccess, ErrorCode.INVALID_TOKEN);
  }

  @Test
  void legacyTypeAssertionsUseNormalizedJwtError() {
    String legacyRefresh = jwtProvider.createRefreshToken(USER_UUID);

    assertThatThrownBy(() -> jwtProvider.assertAccessToken(legacyRefresh))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_TOKEN));
  }

  @Test
  void fingerprintsCompactTokenWithoutRetainingTheRawValue() {
    String token = jwtProvider.createSignupToken(USER_UUID);

    String fingerprint = JwtFingerprint.sha256(token);

    assertThat(fingerprint).hasSize(64).isEqualTo(JwtFingerprint.sha256(token));
    assertThat(fingerprint).doesNotContain(token);
  }

  private String signedToken(String type, String audience, String sessionId) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    return baseBuilder(type, audience, sessionId).signWith(key, Jwts.SIG.HS256).compact();
  }

  private io.jsonwebtoken.JwtBuilder baseBuilder(String type, String audience, String sessionId) {
    return requiredClaimsBuilder(type, audience).claim("sid", sessionId);
  }

  private io.jsonwebtoken.JwtBuilder requiredClaimsBuilder(String type, String audience) {
    return Jwts.builder()
        .subject(USER_UUID)
        .issuer(ISSUER)
        .audience()
        .add(audience)
        .and()
        .issuedAt(Date.from(NOW))
        .expiration(Date.from(NOW.plus(15, ChronoUnit.MINUTES)))
        .id(UUID.randomUUID().toString())
        .claim("typ", type);
  }

  private void assertError(String token, ErrorCode errorCode) {
    assertThatThrownBy(() -> jwtProvider.verify(token))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }
}
