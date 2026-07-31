package com.poppang.be.domain.auth.application;

import com.poppang.be.common.jwt.JwtProperties;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.domain.auth.redis.TokenHashRecord;
import com.poppang.be.domain.auth.redis.V2SignupTokenRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class V2SocialSignupTokenService {

  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final V2SignupTokenRedisRepository signupTokenRepository;

  V2SocialSignupToken issue(String userUuid) {
    String signupToken = jwtProvider.createSignupToken(userUuid);
    VerifiedJwt verifiedJwt = jwtProvider.verify(signupToken, JwtTokenType.SIGNUP);
    signupTokenRepository.save(userUuid, TokenHashRecord.from(signupToken, verifiedJwt));
    return new V2SocialSignupToken(
        signupToken, Math.multiplyExact(jwtProperties.signupTokenExpMinutes(), 60L));
  }
}
