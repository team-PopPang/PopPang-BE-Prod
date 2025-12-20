package com.poppang.be.domain.auth.apple.util;

// Nimbus JOSE + JWT 라이브러리: JWS 헤더/서명/알고리즘/JWT 생성에 사용

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import org.springframework.core.io.ClassPathResource;

public class AppleJwtUtil {
  /*
  client_secret 생성 메서드
  - Apple “Sign in with Apple” 토큰 교환 시 필요한 client_secret(JWT)을 ES256으로 서명해서 생성
   */
  public static String createClientSecret(AppleProperties properties) throws Exception {
    // 1) .p8 개인키 읽기
    // - application.yml의 apple.private-key-path 값을 이용
    // - 현재 구현은 classpath: 경로만 지원하도록 가정
    String privateKeyPath = properties.getPrivateKeyPath();
    String privateKeyPem;

    if (privateKeyPath.startsWith("classpath:")) {
      // "classpath:" 접두어 제거 후 /resources 아래에서 파일 읽기
      String path = privateKeyPath.replace("classpath:", "");
      ClassPathResource resource = new ClassPathResource(path);
      privateKeyPem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } else {
      // 보안/운영상 외부 경로를 사용할 수도 있으나, 이 유틸은 일단 classpath만 허용
      throw new IllegalArgumentException(
          "Only classpath: resource loading is supported in this setup.");
    }

    // 2) PEM 텍스트 정리
    // - -----BEGIN/END PRIVATE KEY----- 헤더/푸터 제거
    // - 공백/개행 모두 제거 → 순수 Base64 본문만 남김
    privateKeyPem =
        privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

    // 3) PKCS#8 바이너리를 PrivateKey 객체로 변환
    // - Apple의 .p8은 PKCS#8 포맷의 EC(서명 알고리즘: ES256) 개인키
    byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(privateKeyPem);
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
    ECPrivateKey privateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(keySpec);

    // 4) JWT Header 구성
    // - alg: ES256 (P-256 + SHA-256) ← Apple이 요구
    // - kid: Apple 개발자 콘솔의 Key ID
    // - typ: JWT
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(properties.getKeyId())
            .type(JOSEObjectType.JWT)
            .build();

    // 5) JWT Claims 구성
    // - iss: Apple Developer Team ID
    // - iat: 발급시각
    // - exp: 만료시각 (예: 30분)  *Apple은 최대 6개월까지 허용하지만, 짧게 가져가면 보안상 유리
    // - aud: 고정값 "https://appleid.apple.com"
    // - sub: client_id (iOS는 bundle id, Web은 Service ID)
    long now = System.currentTimeMillis() / 1000; // 초 단위
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .issuer(properties.getTeamId()) // iss
            .issueTime(new Date(now * 1000)) // iat (ms 단위 Date)
            .expirationTime(new Date((now + 1800) * 1000)) // exp = 30분(1800초) 후
            .audience("https://appleid.apple.com") // aud
            .subject(properties.getClientId()) // sub
            .build();

    // 6) JWT 서명
    // - 위 Header + Claims를 합쳐 SignedJWT 생성
    // - ECDSASigner에 EC 개인키를 넣어 ES256으로 서명
    SignedJWT signedJWT = new SignedJWT(header, claimsSet);
    signedJWT.sign(new ECDSASigner(privateKey));

    // 7) 직렬화(문자열)하여 반환 → 이 문자열이 client_secret
    return signedJWT.serialize();
  }
}
