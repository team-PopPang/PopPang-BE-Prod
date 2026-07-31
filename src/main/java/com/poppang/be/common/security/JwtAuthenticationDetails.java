package com.poppang.be.common.security;

import java.time.Instant;

public record JwtAuthenticationDetails(
    String tokenFingerprint, String jwtId, Instant issuedAt, Instant expiresAt) {

  public JwtAuthenticationDetails {
    if (tokenFingerprint == null || !tokenFingerprint.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("tokenFingerprint must be a SHA-256 hex value");
    }
    if (jwtId == null || jwtId.isBlank()) {
      throw new IllegalArgumentException("jwtId must not be blank");
    }
    if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("JWT timestamps are invalid");
    }
  }

  @Override
  public String toString() {
    return "JwtAuthenticationDetails[tokenFingerprint=[REDACTED], jwtId=[REDACTED], issuedAt="
        + issuedAt
        + ", expiresAt="
        + expiresAt
        + "]";
  }
}
