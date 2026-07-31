package com.poppang.be.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

public class ApiAccessDeniedHandler implements AccessDeniedHandler {

  private static final String TOKEN_ACCESS = "TOKEN_ACCESS";
  private static final String ROLE_ADMIN = "ROLE_ADMIN";

  private final ApiAuthenticationEntryPoint responseWriter;

  public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
    this.responseWriter = new ApiAuthenticationEntryPoint(objectMapper);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      org.springframework.security.access.AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    ErrorCode fallback =
        isMemberAccessingAdmin(request)
            ? ErrorCode.ACCESS_DENIED
            : ErrorCode.INSUFFICIENT_AUTHORITY;
    responseWriter.write(response, ApiAuthenticationEntryPoint.errorCode(request, fallback));
  }

  private boolean isMemberAccessingAdmin(HttpServletRequest request) {
    if (!request.getRequestURI().startsWith("/api/v2/admin/")) {
      return false;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    boolean hasAccessToken = hasAuthority(authentication, TOKEN_ACCESS);
    boolean hasAdminRole = hasAuthority(authentication, ROLE_ADMIN);
    return hasAccessToken && !hasAdminRole;
  }

  private boolean hasAuthority(Authentication authentication, String expected) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(expected::equals);
  }
}
