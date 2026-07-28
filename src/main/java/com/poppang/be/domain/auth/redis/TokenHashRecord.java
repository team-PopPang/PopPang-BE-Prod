package com.poppang.be.domain.auth.redis;

import com.poppang.be.common.jwt.JwtFingerprint;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import java.time.Instant;
import java.util.Objects;

public record TokenHashRecord(
    String tokenHash, String jwtId, String sessionId, Instant issuedAt, Instant expiresAt) {

  public TokenHashRecord {
    if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("tokenHash must be a SHA-256 hex value");
    }
    if (jwtId == null || jwtId.isBlank()) {
      throw new IllegalArgumentException("jwtId must not be blank");
    }
    if (sessionId != null && (sessionId.isBlank() || sessionId.contains(":"))) {
      throw new IllegalArgumentException(
          "sessionId must be null, non-blank and must not contain ':'");
    }
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
  }

  public static TokenHashRecord from(String compactToken, VerifiedJwt verifiedJwt) {
    Objects.requireNonNull(verifiedJwt, "verifiedJwt must not be null");
    JwtTokenType tokenType = Objects.requireNonNull(verifiedJwt.tokenType());
    if (tokenType != JwtTokenType.REFRESH && tokenType != JwtTokenType.SIGNUP) {
      throw new IllegalArgumentException("Access Token cannot be stored as an auth token record");
    }
    if (tokenType == JwtTokenType.REFRESH
        && (verifiedJwt.sessionId() == null || verifiedJwt.sessionId().isBlank())) {
      throw new IllegalArgumentException("Refresh Token requires a sessionId");
    }
    if (tokenType == JwtTokenType.SIGNUP && verifiedJwt.sessionId() != null) {
      throw new IllegalArgumentException("Signup Token must not have a sessionId");
    }
    return new TokenHashRecord(
        JwtFingerprint.sha256(compactToken),
        verifiedJwt.jwtId(),
        verifiedJwt.sessionId(),
        verifiedJwt.issuedAt(),
        verifiedJwt.expiresAt());
  }

  String serializedValue() {
    String tokenFields = tokenHash + ":" + jwtId + ":" + issuedAt.toEpochMilli();
    return sessionId == null ? tokenFields : sessionId + ":" + tokenFields;
  }

  @Override
  public String toString() {
    return "TokenHashRecord[tokenHash=[REDACTED], jwtId=[REDACTED], sessionId=[REDACTED], issuedAt="
        + issuedAt
        + ", expiresAt="
        + expiresAt
        + "]";
  }
}
