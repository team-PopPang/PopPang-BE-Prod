package com.poppang.be.common.jwt;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtProvider {

  private static final String CLAIM_TOKEN_TYPE = "typ";

  private final JwtProperties props;
  private final SecretKey key;

  public JwtProvider(JwtProperties props) {
    this.props = props;

    String s = props.secret();
    log.info("JWT secret length={}", s == null ? -1 : s.length());

    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  public String createAccessToken(String userUuid) {
    Instant exp = Instant.now().plus(props.accessTokenExpMinutes(), ChronoUnit.MINUTES);
    return createToken(userUuid, JwtTokenType.ACCESS, exp);
  }

  public String createRefreshToken(String userUuid) {
    Instant exp = Instant.now().plus(props.refreshTokenExpDays(), ChronoUnit.DAYS);
    return createToken(userUuid, JwtTokenType.REFRESH, exp);
  }

  private String createToken(String userUuid, JwtTokenType type, Instant expiresAt) {
    Instant now = Instant.now();

    return Jwts.builder()
        .subject(userUuid) //  sub = userUuid
        .issuer(props.issuer())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .claim(CLAIM_TOKEN_TYPE, type.name())
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  /** 서명/만료/형식 검증 + Claims 반환 */
  public Claims parseAndValidate(String token) {
    try {
      return Jwts.parser()
          .verifyWith(key)
          .requireIssuer(props.issuer())
          .build()
          .parseSignedClaims(token)
          .getPayload();

    } catch (ExpiredJwtException e) {
      throw new BaseException(ErrorCode.EXPIRED_TOKEN);

    } catch (UnsupportedJwtException e) {
      throw new BaseException(ErrorCode.UNSUPPORTED_TOKEN);

    } catch (MalformedJwtException e) {
      throw new BaseException(ErrorCode.MALFORMED_TOKEN);

    } catch (SecurityException e) {
      throw new BaseException(ErrorCode.TOKEN_SIGNATURE_INVALID);

    } catch (JwtException e) { // 그 외 JWT 관련 예외
      throw new BaseException(ErrorCode.INVALID_TOKEN);

    } catch (IllegalArgumentException e) {
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }
  }

  public String getUserUuid(String token) {
    return parseAndValidate(token).getSubject();
  }

  public JwtTokenType getTokenType(String token) {
    String type = parseAndValidate(token).get(CLAIM_TOKEN_TYPE, String.class);
    return JwtTokenType.valueOf(type);
  }

  public void assertAccessToken(String token) {
    if (getTokenType(token) != JwtTokenType.ACCESS) {
      throw new RuntimeException("Not an access token");
    }
  }

  public void assertRefreshToken(String token) {
    if (getTokenType(token) != JwtTokenType.REFRESH) {
      throw new RuntimeException("Not a refresh token");
    }
  }
}
