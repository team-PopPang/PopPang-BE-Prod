package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class V1AuthImplementationIsolationContractTest {

  private static final Path AUTH_SOURCE_ROOT = Path.of("src/main/java/com/poppang/be/domain/auth");

  @Test
  void v1SocialAuthImplementationsDoNotDependOnV2CredentialVerifiers() throws IOException {
    for (Path source :
        List.of(
            AUTH_SOURCE_ROOT.resolve("kakao/application/KakaoAuthServiceImpl.java"),
            AUTH_SOURCE_ROOT.resolve("google/application/GoogleAuthServiceImpl.java"),
            AUTH_SOURCE_ROOT.resolve("apple/application/AppleAuthServiceImpl.java"))) {
      String content = Files.readString(source);

      assertThat(content)
          .as("%s must remain independent from v2 authentication code", source)
          .doesNotContain("CredentialVerifier", "VerifiedSocialIdentity", "V2");
    }
  }
}
