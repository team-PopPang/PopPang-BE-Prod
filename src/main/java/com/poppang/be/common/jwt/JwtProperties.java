package com.poppang.be.common.jwt;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    long accessTokenExpMinutes,
    long refreshTokenExpDays,
    String issuer,
    String audience,
    String signupAudience,
    long signupTokenExpMinutes) {

  private static final int MINIMUM_HS256_SECRET_BYTES = 32;
  private static final String DEFAULT_V2_AUDIENCE = "poppang-app-v2";
  private static final String DEFAULT_SIGNUP_AUDIENCE = "poppang-signup-v2";
  private static final long DEFAULT_SIGNUP_EXPIRATION_MINUTES = 15;

  /** Legacy tests and callers can keep constructing the original four-property shape. */
  public JwtProperties(
      String secret, long accessTokenExpMinutes, long refreshTokenExpDays, String issuer) {
    this(
        secret,
        accessTokenExpMinutes,
        refreshTokenExpDays,
        issuer,
        DEFAULT_V2_AUDIENCE,
        DEFAULT_SIGNUP_AUDIENCE,
        DEFAULT_SIGNUP_EXPIRATION_MINUTES);
  }

  @ConstructorBinding
  public JwtProperties {
    if (secret == null
        || secret.isBlank()
        || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HS256_SECRET_BYTES) {
      throw new IllegalArgumentException("jwt.secret must be at least 256 bits");
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("jwt.issuer must not be blank");
    }
    if (audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("jwt.audience must not be blank");
    }
    if (signupAudience == null || signupAudience.isBlank()) {
      throw new IllegalArgumentException("jwt.signup-audience must not be blank");
    }
    if (audience.equals(signupAudience)) {
      throw new IllegalArgumentException("jwt audiences must be different");
    }
    if (accessTokenExpMinutes <= 0) {
      throw new IllegalArgumentException("jwt.access-token-exp-minutes must be positive");
    }
    if (refreshTokenExpDays <= 0) {
      throw new IllegalArgumentException("jwt.refresh-token-exp-days must be positive");
    }
    if (signupTokenExpMinutes <= 0) {
      throw new IllegalArgumentException("jwt.signup-token-exp-minutes must be positive");
    }
  }

  @Override
  public String toString() {
    return "JwtProperties[secret=[REDACTED], accessTokenExpMinutes="
        + accessTokenExpMinutes
        + ", refreshTokenExpDays="
        + refreshTokenExpDays
        + ", issuer="
        + issuer
        + ", audience="
        + audience
        + ", signupAudience="
        + signupAudience
        + ", signupTokenExpMinutes="
        + signupTokenExpMinutes
        + "]";
  }
}
