package com.poppang.be.domain.auth.apple.util;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URL;

public class AppleJwtVerifier {

  private static final String ISSUER = "https://appleid.apple.com";

  /**
   * Apple id_token 검증 (DefaultJWTProcessor 방식)
   *
   * @param idToken Apple 서버에서 받은 id_token (JWT)
   * @param clientId 내 앱의 client_id (bundle ID or Service ID)
   * @return JWTClaimsSet (sub, email 등 클레임 포함)
   */
  public static JWTClaimsSet verifyIdToken(String idToken, String clientId) throws Exception {
    // 1) Apple 공개키(JWKs) 엔드포인트
    URL jwksURL = new URL("https://appleid.apple.com/auth/keys");

    // 2) 원격 JWK 소스 설정 (Nimbus가 자동으로 캐싱 + kid 매칭 처리)
    JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwksURL);

    // 3) JWT Processor 생성
    ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();

    // 4) 어떤 알고리즘으로 서명 검증할지 선택 (Apple은 RS256 or ES256 → 보통 RS256)
    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

    jwtProcessor.setJWSKeySelector(keySelector);

    // 5) id_token 검증 및 클레임 추출
    JWTClaimsSet claims = jwtProcessor.process(idToken, null);

    // 6) iss(발급자) 검증
    if (!ISSUER.equals(claims.getIssuer())) {
      throw new IllegalArgumentException("❌ Invalid issuer: " + claims.getIssuer());
    }

    // 7) aud(내 앱 client_id) 검증
    if (!claims.getAudience().contains(clientId)) {
      throw new IllegalArgumentException("❌ Invalid audience: " + claims.getAudience());
    }

    // 8) 만료시간(exp) 검증
    if (claims.getExpirationTime().before(new java.util.Date())) {
      throw new IllegalArgumentException("❌ id_token expired");
    }

    return claims;
  }
}
