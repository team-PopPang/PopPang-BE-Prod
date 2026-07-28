package com.poppang.be.common.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class JwtFingerprint {

  private JwtFingerprint() {}

  public static String sha256(String compactToken) {
    if (compactToken == null || compactToken.isBlank()) {
      throw new IllegalArgumentException("compactToken must not be blank");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(compactToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
