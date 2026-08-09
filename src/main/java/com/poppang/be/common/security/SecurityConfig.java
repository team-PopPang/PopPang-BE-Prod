package com.poppang.be.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimitProperties;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.domain.auth.config.QaTokenProperties;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
  WorkerApiKeyProperties.class,
  QaTokenProperties.class,
  V2AuthRateLimitProperties.class
})
public class SecurityConfig {

  private static final String[] DEFAULT_SWAGGER_PATHS = {
    "/swagger-ui",
    "/swagger-ui.html",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/v3/api-docs.yaml",
    "/v3/api-docs.yaml/**"
  };

  static final RequestMatcher V2_PUBLIC_ENDPOINTS =
      new OrRequestMatcher(
          matcher(HttpMethod.POST, "/api/v2/auth/kakao/mobile/login"),
          matcher(HttpMethod.POST, "/api/v2/auth/google/mobile/login"),
          matcher(HttpMethod.POST, "/api/v2/auth/apple/mobile/login"),
          matcher(HttpMethod.POST, "/api/v2/auth/refresh"),
          matcher(HttpMethod.GET, "/api/v2/web/**"),
          matcher(HttpMethod.HEAD, "/api/v2/web/**"));

  private static final RequestMatcher V2_SIGNUP_ENDPOINTS =
      new OrRequestMatcher(
          matcher(HttpMethod.POST, "/api/v2/auth/kakao/signup"),
          matcher(HttpMethod.POST, "/api/v2/auth/google/signup"),
          matcher(HttpMethod.POST, "/api/v2/auth/apple/signup"));

  private final boolean apiDocsEnabled;
  private final String apiDocsPath;
  private final boolean swaggerUiEnabled;
  private final String swaggerUiPath;

  public SecurityConfig(
      @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
      @Value("${springdoc.api-docs.path:}") String apiDocsPath,
      @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled,
      @Value("${springdoc.swagger-ui.path:}") String swaggerUiPath) {
    this.apiDocsEnabled = apiDocsEnabled;
    this.apiDocsPath = apiDocsPath;
    this.swaggerUiEnabled = swaggerUiEnabled;
    this.swaggerUiPath = swaggerUiPath;
  }

  @Bean
  ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
    return new ApiAuthenticationEntryPoint(objectMapper);
  }

  @Bean
  ApiAccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper) {
    return new ApiAccessDeniedHandler(objectMapper);
  }

  @Bean
  V2JwtAuthenticationFilter v2JwtAuthenticationFilter(
      JwtProvider jwtProvider,
      UsersRepository usersRepository,
      ApiAuthenticationEntryPoint authenticationEntryPoint,
      ApiAccessDeniedHandler accessDeniedHandler) {
    return new V2JwtAuthenticationFilter(
        jwtProvider, usersRepository, authenticationEntryPoint, accessDeniedHandler);
  }

  @Bean
  WorkerApiKeyAuthenticationFilter workerApiKeyAuthenticationFilter(
      WorkerApiKeyProperties properties, ApiAuthenticationEntryPoint authenticationEntryPoint) {
    return new WorkerApiKeyAuthenticationFilter(properties, authenticationEntryPoint);
  }

  @Bean
  QaApiKeyAuthenticationFilter qaApiKeyAuthenticationFilter(
      QaTokenProperties properties, ApiAuthenticationEntryPoint authenticationEntryPoint) {
    return new QaApiKeyAuthenticationFilter(properties, authenticationEntryPoint);
  }

  @Bean
  V2AuthRateLimitFilter v2AuthRateLimitFilter(
      V2AuthRateLimiter rateLimiter, ApiAuthenticationEntryPoint authenticationEntryPoint) {
    return new V2AuthRateLimitFilter(rateLimiter, authenticationEntryPoint);
  }

  @Bean
  FilterRegistrationBean<V2JwtAuthenticationFilter> v2JwtFilterRegistration(
      V2JwtAuthenticationFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  FilterRegistrationBean<WorkerApiKeyAuthenticationFilter> workerApiKeyFilterRegistration(
      WorkerApiKeyAuthenticationFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  FilterRegistrationBean<QaApiKeyAuthenticationFilter> qaApiKeyFilterRegistration(
      QaApiKeyAuthenticationFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  FilterRegistrationBean<V2AuthRateLimitFilter> v2AuthRateLimitFilterRegistration(
      V2AuthRateLimitFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  @Order(1)
  SecurityFilterChain internalSecurityFilterChain(
      HttpSecurity http,
      WorkerApiKeyAuthenticationFilter workerFilter,
      ApiAuthenticationEntryPoint authenticationEntryPoint,
      ApiAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    stateless(http);
    return http.securityMatcher("/api/v2/internal/**")
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .anyRequest()
                    .hasAuthority(WorkerApiKeyAuthenticationFilter.SERVICE_WORKER))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(workerFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain qaTokenSecurityFilterChain(
      HttpSecurity http,
      QaApiKeyAuthenticationFilter qaApiKeyFilter,
      ApiAuthenticationEntryPoint authenticationEntryPoint,
      ApiAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    stateless(http);
    return http.securityMatcher(matcher(HttpMethod.POST, "/api/v2/test-auth/token"))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .anyRequest()
                    .hasAuthority(QaApiKeyAuthenticationFilter.QA_TOKEN_ISSUER))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(qaApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  @Order(3)
  SecurityFilterChain v2SecurityFilterChain(
      HttpSecurity http,
      V2JwtAuthenticationFilter jwtFilter,
      V2AuthRateLimitFilter rateLimitFilter,
      ApiAuthenticationEntryPoint authenticationEntryPoint,
      ApiAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    stateless(http);
    return http.securityMatcher("/api/v2/**")
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(V2_PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers(V2_SIGNUP_ENDPOINTS)
                    .hasAuthority(V2JwtAuthenticationFilter.TOKEN_SIGNUP)
                    .requestMatchers("/api/v2/admin/**")
                    .access(
                        new WebExpressionAuthorizationManager(
                            "hasAuthority('TOKEN_ACCESS') and hasRole('ADMIN')"))
                    .anyRequest()
                    .hasAuthority(V2JwtAuthenticationFilter.TOKEN_ACCESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, V2JwtAuthenticationFilter.class)
        .build();
  }

  @Bean
  @Order(4)
  SecurityFilterChain v1SecurityFilterChain(HttpSecurity http) throws Exception {
    stateless(http);
    return http.securityMatcher("/api/v1/**")
        .authorizeHttpRequests(authorization -> authorization.anyRequest().permitAll())
        .build();
  }

  @Bean
  @Order(5)
  SecurityFilterChain infrastructureSecurityFilterChain(
      HttpSecurity http,
      ApiAuthenticationEntryPoint authenticationEntryPoint,
      ApiAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    stateless(http);
    return http.authorizeHttpRequests(
            authorization -> {
              String[] permittedSwaggerPaths = permittedSwaggerPaths();
              if (permittedSwaggerPaths.length > 0) {
                authorization.requestMatchers(permittedSwaggerPaths).permitAll();
              }

              authorization
                  .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                  .permitAll()
                  .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/health/**")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/submissionImages/**")
                  .permitAll()
                  .requestMatchers(HttpMethod.HEAD, "/submissionImages/**")
                  .permitAll()
                  .requestMatchers("/error")
                  .permitAll()
                  .requestMatchers(DEFAULT_SWAGGER_PATHS)
                  .denyAll()
                  .anyRequest()
                  .denyAll();
            })
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          ApiAuthenticationEntryPoint.setError(
                              request, ErrorCode.INSUFFICIENT_AUTHORITY);
                          authenticationEntryPoint.commence(request, response, exception);
                        })
                    .accessDeniedHandler(accessDeniedHandler))
        .build();
  }

  private void stateless(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }

  private String[] permittedSwaggerPaths() {
    List<String> paths = new ArrayList<>();

    if (apiDocsEnabled && hasText(apiDocsPath)) {
      addPathAndChildren(paths, apiDocsPath);
    }

    if (swaggerUiEnabled && hasText(swaggerUiPath)) {
      addPathAndChildren(paths, swaggerUiPath);
      paths.add("/swagger-ui/**");
    }

    return paths.toArray(String[]::new);
  }

  private void addPathAndChildren(List<String> paths, String path) {
    String normalizedPath = normalizePath(path);
    paths.add(normalizedPath);
    paths.add(normalizedPath + "/**");
  }

  private String normalizePath(String path) {
    String normalizedPath = path.trim();
    if (!normalizedPath.startsWith("/")) {
      normalizedPath = "/" + normalizedPath;
    }
    if (normalizedPath.length() > 1 && normalizedPath.endsWith("/")) {
      normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
    }
    return normalizedPath;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static RequestMatcher matcher(HttpMethod method, String pattern) {
    return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
  }

  private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(
      T filter) {
    FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
