package com.poppang.be.domain.auth.apple.util;

// Nimbus JOSE + JWT 라이브러리: JWS 헤더/서명/알고리즘/JWT 생성에 사용

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

public class AppleJwtUtil {

  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final String FILE_PREFIX = "file:";

  /*
  client_secret 생성 메서드
  - Apple “Sign in with Apple” 토큰 교환 시 필요한 client_secret(JWT)을 ES256으로 서명해서 생성
   */
  public static String createClientSecret(AppleProperties properties) throws Exception {
    // 1) .p8 개인키 읽기
    // - apple.private-key-path 값을 이용
    // - 운영: 컨테이너에 읽기 전용으로 마운트한 외부 파일 경로(절대경로 또는 file:)
    // - 로컬 개발: classpath: 경로도 계속 지원
    String privateKeyPem = readPrivateKeyPem(properties.getPrivateKeyPath());

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

  // 키 파일이 없거나 읽을 수 없으면 다른 위치로 대체하지 않고 예외를 던진다.
  private static String readPrivateKeyPem(String location) throws IOException {
    if (!StringUtils.hasText(location)) {
      throw new IllegalStateException("apple.private-key-path가 설정되지 않았습니다.");
    }

    if (location.startsWith(CLASSPATH_PREFIX)) {
      ClassPathResource resource =
          new ClassPathResource(location.substring(CLASSPATH_PREFIX.length()));
      try (InputStream inputStream = resource.getInputStream()) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      }
    }

    Path path =
        location.startsWith(FILE_PREFIX)
            ? ResourceUtils.getFile(location).toPath()
            : Path.of(location);
    return Files.readString(path, StandardCharsets.UTF_8);
  }
}
