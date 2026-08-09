package com.poppang.be.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String ACCESS_BEARER = "bearerAccessAuth";
  private static final String SIGNUP_BEARER = "bearerSignupAuth";
  private static final String WORKER_API_KEY = "workerApiKeyAuth";
  private static final String QA_API_KEY = "qaApiKeyAuth";

  @Bean
  public GroupedOpenApi v1OpenApi() {
    return GroupedOpenApi.builder().group("v1").pathsToMatch("/api/v1/**").build();
  }

  @Bean
  public GroupedOpenApi v2OpenApi() {
    return GroupedOpenApi.builder()
        .group("v2")
        .pathsToMatch("/api/v2/**")
        .addOpenApiCustomizer(this::customizeV2Security)
        .build();
  }

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    ACCESS_BEARER,
                    bearerScheme("v2 Access Token. /api/v2/admin/**는 ROLE_ADMIN도 필요합니다."))
                .addSecuritySchemes(
                    SIGNUP_BEARER, bearerScheme("v2 Signup Token. provider 회원가입에만 사용합니다."))
                .addSecuritySchemes(
                    WORKER_API_KEY,
                    new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
                        .in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
                        .name("X-Worker-Api-Key")
                        .description("v2 internal worker 전용 API Key"))
                .addSecuritySchemes(
                    QA_API_KEY,
                    new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
                        .in(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
                        .name("X-QA-Api-Key")
                        .description("v2 고정 QA 계정 토큰 발급용 공통 API Key")))
        .info(
            new Info()
                .title("PopPang API 리스트")
                .description("Poppang 서비스 API 문서")
                .version("v1.0.0"));
  }

  private io.swagger.v3.oas.models.security.SecurityScheme bearerScheme(String description) {
    return new io.swagger.v3.oas.models.security.SecurityScheme()
        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description(description);
  }

  private void customizeV2Security(OpenAPI openApi) {
    if (openApi.getPaths() == null) {
      return;
    }
    openApi
        .getPaths()
        .forEach(
            (path, pathItem) ->
                pathItem
                    .readOperationsMap()
                    .forEach(
                        (method, operation) ->
                            operation.setSecurity(securityFor(method.name(), path))));
  }

  private List<SecurityRequirement> securityFor(String method, String path) {
    String operation = method + " " + path;
    if (path.startsWith("/api/v2/internal/")) {
      return requirement(WORKER_API_KEY);
    }
    if (operation.equals("POST /api/v2/test-auth/token")) {
      return requirement(QA_API_KEY);
    }
    if ((method.equals("GET") || method.equals("HEAD")) && path.startsWith("/api/v2/web/")) {
      return List.of();
    }
    if (operation.matches("POST /api/v2/auth/(kakao|google|apple)/mobile/login")
        || operation.equals("POST /api/v2/auth/refresh")) {
      return List.of();
    }
    if (operation.matches("POST /api/v2/auth/(kakao|google|apple)/signup")) {
      return requirement(SIGNUP_BEARER);
    }
    return requirement(ACCESS_BEARER);
  }

  private List<SecurityRequirement> requirement(String scheme) {
    return List.of(new SecurityRequirement().addList(scheme));
  }
}
