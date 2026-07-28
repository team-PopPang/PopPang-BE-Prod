package com.poppang.be.common.jwt;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  private static final String CLAIM_TOKEN_TYPE = "typ";
  private static final String CLAIM_SESSION_ID = "sid";

  private final JwtProperties props;
  private final SecretKey key;
  private final Clock clock;

  /** Legacy direct construction remains available for existing callers and focused tests. */
  public JwtProvider(JwtProperties props) {
    this(props, Clock.systemUTC());
  }

  @Autowired
  public JwtProvider(JwtProperties props, Clock clock) {
    this.props = props;
    this.clock = clock;
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  /** Legacy token contract. v1 callers must keep working during the migration. */
  public String createAccessToken(String userUuid) {
    return createLegacyToken(
        userUuid,
        JwtTokenType.ACCESS,
        clock.instant().plus(props.accessTokenExpMinutes(), ChronoUnit.MINUTES));
  }

  /** Legacy token contract. v1 callers must keep working during the migration. */
  public String createRefreshToken(String userUuid) {
    return createLegacyToken(
        userUuid,
        JwtTokenType.REFRESH,
        clock.instant().plus(props.refreshTokenExpDays(), ChronoUnit.DAYS));
  }

  public String createAccessToken(String userUuid, String sessionId) {
    return createV2Token(
        userUuid,
        JwtTokenType.ACCESS,
        props.audience(),
        requireSessionId(sessionId),
        props.accessTokenExpMinutes(),
        ChronoUnit.MINUTES);
  }

  public String createRefreshToken(String userUuid, String sessionId) {
    return createV2Token(
        userUuid,
        JwtTokenType.REFRESH,
        props.audience(),
        requireSessionId(sessionId),
        props.refreshTokenExpDays(),
        ChronoUnit.DAYS);
  }

  public String createSignupToken(String userUuid) {
    return createV2Token(
        userUuid,
        JwtTokenType.SIGNUP,
        props.signupAudience(),
        null,
        props.signupTokenExpMinutes(),
        ChronoUnit.MINUTES);
  }

  public String createSessionId() {
    return UUID.randomUUID().toString();
  }

  private String createLegacyToken(String userUuid, JwtTokenType type, Instant expiresAt) {
    Instant now = clock.instant();

    return Jwts.builder()
        .subject(requireUserUuid(userUuid))
        .issuer(props.issuer())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .claim(CLAIM_TOKEN_TYPE, type.name())
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  private String createV2Token(
      String userUuid,
      JwtTokenType type,
      String audience,
      String sessionId,
      long expirationAmount,
      ChronoUnit expirationUnit) {
    Instant now = clock.instant();
    var builder =
        Jwts.builder()
            .subject(requireUserUuid(userUuid))
            .issuer(props.issuer())
            .audience()
            .add(audience)
            .and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expirationAmount, expirationUnit)))
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_TOKEN_TYPE, type.name());

    if (sessionId != null) {
      builder.claim(CLAIM_SESSION_ID, sessionId);
    }

    return builder.signWith(key, Jwts.SIG.HS256).compact();
  }

  /**
   * v2 strict verification. Signature, issuer and algorithm are verified before token-specific
   * claims are used.
   */
  public VerifiedJwt verify(String token) {
    try {
      Jws<Claims> signedClaims =
          Jwts.parser()
              .verifyWith(key)
              .requireIssuer(props.issuer())
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token);

      if (!Jwts.SIG.HS256.getId().equals(signedClaims.getHeader().getAlgorithm())) {
        throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);
      }

      Claims claims = signedClaims.getPayload();
      JwtTokenType tokenType = parseTokenType(claims);
      String audience = requireAudience(claims, tokenType);
      String userUuid = requireClaim(claims.getSubject());
      String jwtId = requireClaim(claims.getId());
      Instant issuedAt = requireDate(claims.getIssuedAt());
      Instant expiresAt = requireDate(claims.getExpiration());
      String sessionId = claims.get(CLAIM_SESSION_ID, String.class);

      validateSessionId(tokenType, sessionId);

      return new VerifiedJwt(userUuid, tokenType, audience, issuedAt, expiresAt, jwtId, sessionId);
    } catch (BaseException exception) {
      throw exception;
    } catch (ExpiredJwtException exception) {
      throw new BaseException(ErrorCode.EXPIRED_TOKEN);
    } catch (UnsupportedJwtException exception) {
      throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);
    } catch (MalformedJwtException exception) {
      throw new BaseException(ErrorCode.MALFORMED_TOKEN);
    } catch (io.jsonwebtoken.security.SecurityException exception) {
      throw new BaseException(ErrorCode.TOKEN_SIGNATURE_INVALID);
    } catch (JwtException | IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
  }

  public VerifiedJwt verify(String token, JwtTokenType expectedType) {
    VerifiedJwt verifiedJwt = verify(token);
    if (verifiedJwt.tokenType() != expectedType) {
      throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);
    }
    return verifiedJwt;
  }

  /** Legacy parser kept for v1 migration compatibility. v2 callers must use {@link #verify}. */
  public Claims parseAndValidate(String token) {
    try {
      return Jwts.parser()
          .verifyWith(key)
          .requireIssuer(props.issuer())
          .clock(() -> Date.from(clock.instant()))
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException exception) {
      throw new BaseException(ErrorCode.EXPIRED_TOKEN);
    } catch (UnsupportedJwtException exception) {
      throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);
    } catch (MalformedJwtException exception) {
      throw new BaseException(ErrorCode.MALFORMED_TOKEN);
    } catch (io.jsonwebtoken.security.SecurityException exception) {
      throw new BaseException(ErrorCode.TOKEN_SIGNATURE_INVALID);
    } catch (JwtException | IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
  }

  public String getUserUuid(String token) {
    return parseAndValidate(token).getSubject();
  }

  public JwtTokenType getTokenType(String token) {
    return parseTokenType(parseAndValidate(token));
  }

  public void assertAccessToken(String token) {
    if (getTokenType(token) != JwtTokenType.ACCESS) {
      throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);
    }
  }

  public void assertRefreshToken(String token) {
    if (getTokenType(token) != JwtTokenType.REFRESH) {
      throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);
    }
  }

  private JwtTokenType parseTokenType(Claims claims) {
    try {
      String value = claims.get(CLAIM_TOKEN_TYPE, String.class);
      return JwtTokenType.valueOf(value);
    } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
      throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);
    }
  }

  private String requireAudience(Claims claims, JwtTokenType tokenType) {
    Set<String> audiences = claims.getAudience();
    String expectedAudience =
        tokenType == JwtTokenType.SIGNUP ? props.signupAudience() : props.audience();
    if (audiences == null || audiences.size() != 1 || !audiences.contains(expectedAudience)) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
    return expectedAudience;
  }

  private void validateSessionId(JwtTokenType tokenType, String sessionId) {
    if (tokenType == JwtTokenType.SIGNUP) {
      if (sessionId != null) {
        throw new BaseException(ErrorCode.INVALID_TOKEN);
      }
      return;
    }
    if (sessionId == null || sessionId.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
    try {
      if (!UUID.fromString(sessionId).toString().equals(sessionId)) {
        throw new BaseException(ErrorCode.INVALID_TOKEN);
      }
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
  }

  private Instant requireDate(Date date) {
    if (date == null) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
    return date.toInstant();
  }

  private String requireClaim(String value) {
    if (value == null || value.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
    return value;
  }

  private String requireUserUuid(String userUuid) {
    if (userUuid == null || userUuid.isBlank()) {
      throw new IllegalArgumentException("userUuid must not be blank");
    }
    return userUuid;
  }

  private String requireSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("sessionId must not be blank");
    }
    try {
      if (!UUID.fromString(sessionId).toString().equals(sessionId)) {
        throw new IllegalArgumentException("sessionId must be a canonical UUID");
      }
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("sessionId must be a canonical UUID");
    }
    return sessionId;
  }
}
