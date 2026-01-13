package com.poppang.be.domain.auth.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtProperties;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.domain.auth.dto.response.AccessTokenResponseDto;
import com.poppang.be.domain.auth.dto.response.TokenResponseDto;
import com.poppang.be.domain.auth.redis.RefreshTokenRedisRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenService {

  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final RefreshTokenRedisRepository refreshTokenRedisRepository;

  @Transactional
  public TokenResponseDto issueTokens(String userUuid) {
    // 1.토큰 생성
    String accessToken = jwtProvider.createAccessToken(userUuid);
    String refreshToken = jwtProvider.createRefreshToken(userUuid);

    // 2. refreshToken -> Redis에 저장
    Duration ttl = Duration.ofDays(jwtProperties.refreshTokenExpDays());
    refreshTokenRedisRepository.save(userUuid, refreshToken, ttl);

    // 3. 응답
    return TokenResponseDto.builder()
        .tokenType("Bearer")
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .build();
  }

  public AccessTokenResponseDto refreshAccessToken(String refreshToken) {
    // 1. refreshToken 검증 + typ 확인
    jwtProvider.assertRefreshToken(refreshToken);

    // 2. refreshToken에서 userUuid 추출
    String userUuid = jwtProvider.getUserUuid(refreshToken);

    // 3. Redis에 저장된 refreshToken 조회
    String saved =
        refreshTokenRedisRepository
            .find(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.INVALID_TOKEN));

    // 4. 일치 비교(핵심)
    if (!saved.equals(refreshToken)) {
      // 탈취, 해킹 등 행위 의심해보기
      throw new BaseException(ErrorCode.INVALID_TOKEN);
    }

    // 5. 새 accessToken 발급
    String newAccessToken = jwtProvider.createAccessToken(userUuid);

    return new AccessTokenResponseDto("Bearer", newAccessToken);
  }
}
