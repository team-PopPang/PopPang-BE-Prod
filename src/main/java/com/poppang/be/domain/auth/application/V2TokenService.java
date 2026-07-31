package com.poppang.be.domain.auth.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtProperties;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.common.ratelimit.V2AuthRateLimitScope;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.auth.redis.TokenHashRecord;
import com.poppang.be.domain.auth.redis.V2RefreshTokenRedisRepository;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V2TokenService {

  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final V2RefreshTokenRedisRepository refreshTokenRepository;
  private final UsersRepository usersRepository;
  private final V2AuthRateLimiter authRateLimiter;

  public V2TokenResponseDto issueTokens(String userUuid) {
    String sessionId = jwtProvider.createSessionId();
    IssuedTokenPair issued = issuePair(userUuid, sessionId);
    refreshTokenRepository.save(userUuid, issued.refreshRecord());
    return issued.response();
  }

  public V2TokenResponseDto refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_REFRESH_REQUEST);
    }

    VerifiedJwt currentJwt = jwtProvider.verify(refreshToken, JwtTokenType.REFRESH);
    Users user =
        usersRepository
            .findByUuid(currentJwt.userUuid())
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE));
    if (user.isDeleted()) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    if (user.getSignupStatus() != SignupStatus.COMPLETED) {
      throw new BaseException(ErrorCode.INSUFFICIENT_AUTHORITY);
    }

    authRateLimiter.check(V2AuthRateLimitScope.REFRESH, currentJwt.userUuid());
    TokenHashRecord current = TokenHashRecord.from(refreshToken, currentJwt);
    IssuedTokenPair replacement = issuePair(currentJwt.userUuid(), currentJwt.sessionId());
    boolean rotated =
        refreshTokenRepository.rotate(currentJwt.userUuid(), current, replacement.refreshRecord());
    if (!rotated) {
      throw new BaseException(ErrorCode.REFRESH_TOKEN_MISMATCH);
    }
    return replacement.response();
  }

  public void logout(String userUuid, String sessionId) {
    refreshTokenRepository.deleteIfSessionMatches(userUuid, sessionId);
  }

  private IssuedTokenPair issuePair(String userUuid, String sessionId) {
    String accessToken = jwtProvider.createAccessToken(userUuid, sessionId);
    String refreshToken = jwtProvider.createRefreshToken(userUuid, sessionId);
    VerifiedJwt refreshJwt = jwtProvider.verify(refreshToken, JwtTokenType.REFRESH);
    TokenHashRecord refreshRecord = TokenHashRecord.from(refreshToken, refreshJwt);
    V2TokenResponseDto response =
        new V2TokenResponseDto(
            "Bearer",
            accessToken,
            refreshToken,
            Math.multiplyExact(jwtProperties.accessTokenExpMinutes(), 60L),
            Math.multiplyExact(jwtProperties.refreshTokenExpDays(), 86_400L));
    return new IssuedTokenPair(response, refreshRecord);
  }

  private record IssuedTokenPair(V2TokenResponseDto response, TokenHashRecord refreshRecord) {}
}
