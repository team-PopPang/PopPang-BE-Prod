package com.poppang.be;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestRuntimeIsolationContractTest {

  @Test
  void gradleTestRuntimeForcesIsolatedTestProfileAndConfigLocation() {
    assertThat(System.getProperty("spring.profiles.active")).isEqualTo("test");
    assertThat(System.getProperty("spring.config.location"))
        .isEqualTo("classpath:/application-test.yml");
    assertThat(getClass().getClassLoader().getResource("application-test.yml")).isNotNull();
  }
}
