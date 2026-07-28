package com.poppang.be.common.jwt;

import java.time.Instant;
import java.util.Objects;

public record VerifiedJwt(
    String userUuid,
    JwtTokenType tokenType,
    String audience,
    Instant issuedAt,
    Instant expiresAt,
    String jwtId,
    String sessionId) {

  public VerifiedJwt {
    requireNonBlank(userUuid, "userUuid");
    Objects.requireNonNull(tokenType, "tokenType must not be null");
    requireNonBlank(audience, "audience");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    requireNonBlank(jwtId, "jwtId");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    if (tokenType == JwtTokenType.SIGNUP && sessionId != null) {
      throw new IllegalArgumentException("Signup Token must not have a sessionId");
    }
    if (tokenType != JwtTokenType.SIGNUP && (sessionId == null || sessionId.isBlank())) {
      throw new IllegalArgumentException("Access and Refresh Tokens require a sessionId");
    }
  }

  @Override
  public String toString() {
    return "VerifiedJwt[userUuid="
        + userUuid
        + ", tokenType="
        + tokenType
        + ", audience="
        + audience
        + ", issuedAt="
        + issuedAt
        + ", expiresAt="
        + expiresAt
        + ", jwtId=[REDACTED], sessionId=[REDACTED]]";
  }

  private static void requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
