package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.util.ClassUtils;

/**
 * S3용 AWS SDK를 추가해도 Spring이 RestTemplate에 고르는 기본 HTTP 클라이언트(소셜 로그인 호출에 사용)가 바뀌지 않아야 한다. Apache
 * HttpClient 5가 classpath에 들어오면 기본 요청 팩토리가 바뀐다.
 */
class HttpClientSelectionContractTest {

  @Test
  void awsSdkDoesNotBringApacheHttpClient5IntoSpringClientSelection() {
    assertThat(ClassUtils.isPresent("org.apache.hc.client5.http.impl.classic.HttpClients", null))
        .as("Apache HttpClient 5 must not be on the runtime classpath")
        .isFalse();
    assertThat(ClientHttpRequestFactoryBuilder.detect())
        .isNotInstanceOf(HttpComponentsClientHttpRequestFactoryBuilder.class);
  }
}
