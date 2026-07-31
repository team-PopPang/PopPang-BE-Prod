package com.poppang.be.domain.users.presentation.v2;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class V2UsersEndpointContractTest {

  @Test
  void v2UsersControllerExists() {
    assertThatCode(
            () -> Class.forName("com.poppang.be.domain.users.presentation.v2.V2UsersController"))
        .doesNotThrowAnyException();
  }
}
