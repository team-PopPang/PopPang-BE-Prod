package com.poppang.be.domain.auth.apple.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppleJwtUtilTest {

  private static final String CLIENT_ID = "test.client.id";
  private static final String TEAM_ID = "TEAMID1234";
  private static final String KEY_ID = "KEYID12345";

  @TempDir Path tempDir;

  private KeyPair keyPair;
  private Path keyFile;

  @BeforeEach
  void writeThrowawayKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    keyPair = generator.generateKeyPair();
    String body =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(keyPair.getPrivate().getEncoded());
    keyFile = tempDir.resolve("apple.p8");
    Files.writeString(
        keyFile, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
  }

  @Test
  void createsClientSecretFromExternalAbsoluteFilePath() throws Exception {
    assertSignedClientSecret(AppleJwtUtil.createClientSecret(properties(keyFile.toString())));
  }

  @Test
  void createsClientSecretFromFileUrlLocation() throws Exception {
    assertSignedClientSecret(
        AppleJwtUtil.createClientSecret(properties("file:" + keyFile.toAbsolutePath())));
  }

  @Test
  void keepsClasspathLocationForLocalDevelopment() throws Exception {
    Path classpathRoot = Files.createDirectories(tempDir.resolve("classpath-root/auth"));
    Files.copy(keyFile, classpathRoot.resolve("local-test.p8"));
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {tempDir.resolve("classpath-root").toUri().toURL()}, original)) {
      thread.setContextClassLoader(loader);
      assertSignedClientSecret(
          AppleJwtUtil.createClientSecret(properties("classpath:auth/local-test.p8")));
    } finally {
      thread.setContextClassLoader(original);
    }
  }

  @Test
  void failsWithoutFallbackWhenExternalKeyFileIsMissing() {
    String missing = tempDir.resolve("missing.p8").toString();

    assertThatThrownBy(() -> AppleJwtUtil.createClientSecret(properties(missing)))
        .isInstanceOf(IOException.class);
  }

  @Test
  void rejectsBlankKeyLocation() {
    assertThatThrownBy(() -> AppleJwtUtil.createClientSecret(properties(" ")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apple.private-key-path");
  }

  private void assertSignedClientSecret(String clientSecret) throws Exception {
    SignedJWT jwt = SignedJWT.parse(clientSecret);
    assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
    assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
    assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
  }

  private static AppleProperties properties(String privateKeyPath) {
    AppleProperties properties = new AppleProperties();
    properties.setClientId(CLIENT_ID);
    properties.setTeamId(TEAM_ID);
    properties.setKeyId(KEY_ID);
    properties.setPrivateKeyPath(privateKeyPath);
    return properties;
  }
}
