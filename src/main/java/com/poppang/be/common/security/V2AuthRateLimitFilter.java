package com.poppang.be.common.security;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.ratelimit.V2AuthRateLimitScope;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class V2AuthRateLimitFilter extends OncePerRequestFilter {

  private static final RequestMatcher LOGIN_ENDPOINTS =
      new OrRequestMatcher(
          matcher("/api/v2/auth/kakao/mobile/login"),
          matcher("/api/v2/auth/google/mobile/login"),
          matcher("/api/v2/auth/apple/mobile/login"));

  private static final RequestMatcher SIGNUP_ENDPOINTS =
      new OrRequestMatcher(
          matcher("/api/v2/auth/kakao/signup"),
          matcher("/api/v2/auth/google/signup"),
          matcher("/api/v2/auth/apple/signup"));

  private final V2AuthRateLimiter rateLimiter;
  private final ApiAuthenticationEntryPoint responseWriter;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !LOGIN_ENDPOINTS.matches(request) && !SIGNUP_ENDPOINTS.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      if (LOGIN_ENDPOINTS.matches(request)) {
        rateLimiter.check(V2AuthRateLimitScope.LOGIN, request.getRemoteAddr());
      } else {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal user) {
          rateLimiter.check(V2AuthRateLimitScope.SIGNUP, user.userUuid());
        }
      }
      filterChain.doFilter(request, response);
    } catch (BaseException exception) {
      SecurityContextHolder.clearContext();
      responseWriter.write(response, exception.getErrorCode());
    }
  }

  private static RequestMatcher matcher(String pattern) {
    return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, pattern);
  }
}
