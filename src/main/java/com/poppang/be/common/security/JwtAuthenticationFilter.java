package com.poppang.be.common.security;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;
  private final UsersRepository usersRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = resolveBearerToken(request);

    // 1. 토큰이 없으면 그냥 다음으로 넘김 (permitAll or Security가 처리)
    if (!StringUtils.hasText(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      // 2. accessToken 검증 (typ=ACCESS 포함)
      jwtProvider.assertAccessToken(token);

      // 3. userUuid 추출
      String userUuid = jwtProvider.getUserUuid(token);

      //
      Users user =
          usersRepository
              .findByUuid(userUuid)
              .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

      List<SimpleGrantedAuthority> authorities =
          List.of(new SimpleGrantedAuthority(user.getRole().toAuthority()));

      // 4. Authentication 생성 (권한은 일단 기본 MEMBER로 박아두고 시작)
      Authentication auth = new UsernamePasswordAuthenticationToken(userUuid, null, authorities);

      // 5. SecurityContext에 인증 정보 저장
      SecurityContextHolder.getContext().setAuthentication(auth);

      filterChain.doFilter(request, response);
    } catch (BaseException e) {
      // 토큰 문제면 인증 실패 -> 401
      SecurityContextHolder.clearContext();
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
  }

  private String resolveBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (!StringUtils.hasText(header)) return null;
    if (!header.startsWith("Bearer ")) return null;

    return header.substring(7);
  }
}
