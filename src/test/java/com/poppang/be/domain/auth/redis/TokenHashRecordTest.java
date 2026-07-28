package com.poppang.be.domain.auth.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.poppang.be.common.jwt.JwtFingerprint;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenHashRecordTest {

  private static final String RAW_TOKEN = "header.payload.signature";
  private static final Instant ISSUED_AT = Instant.parse("2026-07-21T00:00:00Z");

  @Test
  void createsRefreshRecordWithoutRetainingRawToken() {
    VerifiedJwt verifiedJwt = verified(JwtTokenType.REFRESH, "session-id");

    TokenHashRecord record = TokenHashRecord.from(RAW_TOKEN, verifiedJwt);

    assertThat(record.tokenHash()).isEqualTo(JwtFingerprint.sha256(RAW_TOKEN));
    assertThat(record.jwtId()).isEqualTo("jwt-id");
    assertThat(record.sessionId()).isEqualTo("session-id");
    assertThat(record.issuedAt()).isEqualTo(ISSUED_AT);
    assertThat(record.expiresAt()).isEqualTo(ISSUED_AT.plusSeconds(900));
    assertThat(record.serializedValue()).startsWith("session-id:").doesNotContain(RAW_TOKEN);
    assertThat(record.toString()).doesNotContain(record.tokenHash()).contains("[REDACTED]");
  }

  @Test
  void createsSignupRecordWithoutSession() {
    TokenHashRecord record = TokenHashRecord.from(RAW_TOKEN, verified(JwtTokenType.SIGNUP, null));

    assertThat(record.sessionId()).isNull();
    assertThat(record.serializedValue()).contains(record.tokenHash(), "jwt-id");
  }

  @Test
  void rejectsAccessTokenAndRefreshWithoutSession() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TokenHashRecord.from(RAW_TOKEN, verified(JwtTokenType.ACCESS, "sid")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TokenHashRecord.from(RAW_TOKEN, verified(JwtTokenType.REFRESH, null)));
  }

  private VerifiedJwt verified(JwtTokenType tokenType, String sessionId) {
    return new VerifiedJwt(
        "user-uuid",
        tokenType,
        tokenType == JwtTokenType.SIGNUP ? "signup-audience" : "app-audience",
        ISSUED_AT,
        ISSUED_AT.plusSeconds(900),
        "jwt-id",
        sessionId);
  }
}
