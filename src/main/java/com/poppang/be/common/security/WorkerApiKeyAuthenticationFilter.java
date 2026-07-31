package com.poppang.be.common.security;

import com.poppang.be.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class WorkerApiKeyAuthenticationFilter extends OncePerRequestFilter {

  static final String HEADER_NAME = "X-Worker-Api-Key";
  static final String SERVICE_WORKER = "SERVICE_WORKER";

  private final WorkerApiKeyProperties properties;
  private final ApiAuthenticationEntryPoint authenticationEntryPoint;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String providedApiKey = request.getHeader(HEADER_NAME);
    if (!matches(providedApiKey)) {
      SecurityContextHolder.clearContext();
      ApiAuthenticationEntryPoint.setError(request, ErrorCode.INVALID_WORKER_API_KEY);
      authenticationEntryPoint.commence(request, response, null);
      return;
    }

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            "worker", null, List.of(new SimpleGrantedAuthority(SERVICE_WORKER)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    filterChain.doFilter(request, response);
  }

  private boolean matches(String providedApiKey) {
    if (providedApiKey == null || providedApiKey.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        properties.apiKey().getBytes(StandardCharsets.UTF_8),
        providedApiKey.getBytes(StandardCharsets.UTF_8));
  }
}
