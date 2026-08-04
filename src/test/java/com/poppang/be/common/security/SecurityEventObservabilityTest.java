package com.poppang.be.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

class SecurityEventObservabilityTest {

  @Test
  void authenticationFailureLogsOnlyLowCardinalityCategoryAndErrorCode() throws Exception {
    String sensitiveUserUuid = "11111111-1111-1111-1111-111111111111";
    String sensitiveToken = "secret-access-token";
    MockHttpServletRequest request =
        request("/api/v2/user/" + sensitiveUserUuid, sensitiveToken, "secret-worker-key");
    ApiAuthenticationEntryPoint.setError(request, ErrorCode.INVALID_TOKEN);

    List<String> messages =
        capture(
            ApiAuthenticationEntryPoint.class,
            () ->
                new ApiAuthenticationEntryPoint(new ObjectMapper())
                    .commence(
                        request,
                        new MockHttpServletResponse(),
                        new AuthenticationCredentialsNotFoundException("missing")));

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0))
        .contains(
            "security_event=authentication_failure",
            "status=401",
            "error_code=" + ErrorCode.INVALID_TOKEN.getCode(),
            "endpoint_category=access")
        .doesNotContain(sensitiveUserUuid, sensitiveToken, "secret-worker-key");
  }

  @Test
  void authorizationDenialIsDistinctAndDoesNotLogTargetIdentifiers() throws Exception {
    String sensitivePopupUuid = "sensitive-popup-uuid";
    MockHttpServletRequest request =
        request("/api/v2/admin/popup/" + sensitivePopupUuid, "secret-token", "secret-key");

    List<String> messages =
        capture(
            ApiAccessDeniedHandler.class,
            () ->
                new ApiAccessDeniedHandler(new ObjectMapper())
                    .handle(
                        request,
                        new MockHttpServletResponse(),
                        new AccessDeniedException("denied")));

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0))
        .contains("security_event=authorization_denied", "status=403", "endpoint_category=admin")
        .doesNotContain(sensitivePopupUuid, "secret-token", "secret-key");
  }

  private MockHttpServletRequest request(String uri, String bearerToken, String workerApiKey) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    request.addHeader("Authorization", "Bearer " + bearerToken);
    request.addHeader("X-Worker-Api-Key", workerApiKey);
    return request;
  }

  private List<String> capture(Class<?> loggerOwner, ThrowingRunnable action) throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(loggerOwner);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      action.run();
      return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
