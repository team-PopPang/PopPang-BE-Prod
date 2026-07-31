package com.poppang.be.domain.auth.presentation.v2;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class V2GoogleAppleAuthEndpointContractTest {

  @Test
  void googleAndAppleV2MobileAuthControllersExist() {
    assertThatCode(
            () -> {
              Class.forName("com.poppang.be.domain.auth.presentation.v2.V2GoogleAuthController");
              Class.forName("com.poppang.be.domain.auth.presentation.v2.V2AppleAuthController");
            })
        .doesNotThrowAnyException();
  }
}
