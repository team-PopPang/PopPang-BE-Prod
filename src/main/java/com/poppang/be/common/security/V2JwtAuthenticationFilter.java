package com.poppang.be.common.security;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtFingerprint;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class V2JwtAuthenticationFilter extends OncePerRequestFilter {

  static final String TOKEN_ACCESS = "TOKEN_ACCESS";
  static final String TOKEN_SIGNUP = "TOKEN_SIGNUP";

  private final JwtProvider jwtProvider;
  private final UsersRepository usersRepository;
  private final ApiAuthenticationEntryPoint authenticationEntryPoint;
  private final ApiAccessDeniedHandler accessDeniedHandler;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return SecurityConfig.V2_PUBLIC_ENDPOINTS.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveBearerToken(request);
    if (!StringUtils.hasText(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      VerifiedJwt verifiedJwt = jwtProvider.verify(token);
      if (verifiedJwt.tokenType() == JwtTokenType.REFRESH) {
        deny(request, response, ErrorCode.INSUFFICIENT_AUTHORITY);
        return;
      }

      UsernamePasswordAuthenticationToken authentication =
          verifiedJwt.tokenType() == JwtTokenType.ACCESS
              ? accessAuthentication(verifiedJwt, token, request, response)
              : signupAuthentication(verifiedJwt, token);
      if (authentication == null) {
        return;
      }

      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (BaseException exception) {
      SecurityContextHolder.clearContext();
      ApiAuthenticationEntryPoint.setError(request, exception.getErrorCode());
      authenticationEntryPoint.commence(request, response, null);
    }
  }

  private UsernamePasswordAuthenticationToken accessAuthentication(
      VerifiedJwt verifiedJwt,
      String compactToken,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException, ServletException {
    Users user = usersRepository.findByUuid(verifiedJwt.userUuid()).orElse(null);
    if (user == null) {
      reject(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
      return null;
    }
    if (user.isDeleted()) {
      reject(request, response, ErrorCode.ACCOUNT_NOT_ACTIVE);
      return null;
    }
    if (user.getSignupStatus() != SignupStatus.COMPLETED || user.getRole() == null) {
      deny(request, response, ErrorCode.INSUFFICIENT_AUTHORITY);
      return null;
    }

    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority(TOKEN_ACCESS));
    authorities.add(new SimpleGrantedAuthority(user.getRole().toAuthority()));
    return authentication(verifiedJwt, compactToken, authorities);
  }

  private UsernamePasswordAuthenticationToken signupAuthentication(
      VerifiedJwt verifiedJwt, String compactToken) {
    return authentication(
        verifiedJwt, compactToken, List.of(new SimpleGrantedAuthority(TOKEN_SIGNUP)));
  }

  private UsernamePasswordAuthenticationToken authentication(
      VerifiedJwt verifiedJwt, String compactToken, List<? extends GrantedAuthority> authorities) {
    JwtPrincipal principal =
        new JwtPrincipal(verifiedJwt.userUuid(), verifiedJwt.tokenType(), verifiedJwt.sessionId());
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, authorities);
    authentication.setDetails(
        new JwtAuthenticationDetails(
            JwtFingerprint.sha256(compactToken),
            verifiedJwt.jwtId(),
            verifiedJwt.issuedAt(),
            verifiedJwt.expiresAt()));
    return authentication;
  }

  private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
      throws IOException, ServletException {
    SecurityContextHolder.clearContext();
    ApiAuthenticationEntryPoint.setError(request, errorCode);
    authenticationEntryPoint.commence(request, response, null);
  }

  private void deny(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
      throws IOException, ServletException {
    SecurityContextHolder.clearContext();
    ApiAuthenticationEntryPoint.setError(request, errorCode);
    accessDeniedHandler.handle(request, response, null);
  }

  private String resolveBearerToken(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
      return null;
    }
    String token = authorization.substring("Bearer ".length()).trim();
    return StringUtils.hasText(token) ? token : null;
  }
}
