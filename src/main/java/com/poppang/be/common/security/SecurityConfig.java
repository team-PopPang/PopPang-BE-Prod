package com.poppang.be.common.security;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String[] DEFAULT_SWAGGER_PATHS = {
    "/swagger-ui",
    "/swagger-ui.html",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/v3/api-docs.yaml",
    "/v3/api-docs.yaml/**"
  };

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Value("${springdoc.api-docs.enabled:false}")
  private boolean apiDocsEnabled;

  @Value("${springdoc.api-docs.path:}")
  private String apiDocsPath;

  @Value("${springdoc.swagger-ui.enabled:false}")
  private boolean swaggerUiEnabled;

  @Value("${springdoc.swagger-ui.path:}")
  private String swaggerUiPath;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              String[] permittedSwaggerPaths = permittedSwaggerPaths();
              if (permittedSwaggerPaths.length > 0) {
                auth.requestMatchers(permittedSwaggerPaths).permitAll();
              }

              auth.requestMatchers(DEFAULT_SWAGGER_PATHS).denyAll().anyRequest().permitAll();
            })
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private String[] permittedSwaggerPaths() {
    List<String> paths = new ArrayList<>();

    if (apiDocsEnabled && hasText(apiDocsPath)) {
      addPathAndChildren(paths, apiDocsPath);
    }

    if (swaggerUiEnabled && hasText(swaggerUiPath)) {
      addPathAndChildren(paths, swaggerUiPath);

      // SpringDoc serves UI assets below this path even when the entry URL is customized.
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
}
