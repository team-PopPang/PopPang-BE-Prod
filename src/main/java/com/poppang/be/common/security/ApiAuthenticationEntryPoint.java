package com.poppang.be.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log = LoggerFactory.getLogger(ApiAuthenticationEntryPoint.class);

  static final String ERROR_CODE_ATTRIBUTE =
      ApiAuthenticationEntryPoint.class.getName() + ".ERROR_CODE";

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException, ServletException {
    ErrorCode errorCode = errorCode(request, ErrorCode.AUTHENTICATION_REQUIRED);
    log.warn(
        "security_event=authentication_failure status={} error_code={} endpoint_category={}",
        errorCode.getHttpStatus().value(),
        errorCode.getCode(),
        endpointCategory(request));
    write(response, errorCode);
  }

  static void setError(HttpServletRequest request, ErrorCode errorCode) {
    request.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode);
  }

  static ErrorCode errorCode(HttpServletRequest request, ErrorCode fallback) {
    Object value = request.getAttribute(ERROR_CODE_ATTRIBUTE);
    return value instanceof ErrorCode errorCode ? errorCode : fallback;
  }

  static String endpointCategory(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null) {
      return "infrastructure";
    }
    if (path.startsWith("/api/v2/internal/")) {
      return "internal";
    }
    if (path.startsWith("/api/v2/admin/")) {
      return "admin";
    }
    if (path.startsWith("/api/v2/web/")) {
      return "public_web";
    }
    if (path.startsWith("/api/v2/auth/")) {
      return "auth";
    }
    if (path.startsWith("/api/v2/")) {
      return "access";
    }
    if (path.startsWith("/api/v1/")) {
      return "legacy_v1";
    }
    return "infrastructure";
  }

  void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(errorCode));
  }
}
