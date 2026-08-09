package com.poppang.be.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class V2OpenApiSecurityContractTest {

  @Test
  void publishesSeparateV1AndV2Groups() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(OpenApiConfig.class)) {
      Map<String, GroupedOpenApi> groups = context.getBeansOfType(GroupedOpenApi.class);

      assertThat(groups.values())
          .extracting(GroupedOpenApi::getGroup)
          .containsExactlyInAnyOrder("v1", "v2");
      assertThat(group(groups, "v1").getPathsToMatch()).containsExactly("/api/v1/**");
      assertThat(group(groups, "v2").getPathsToMatch()).containsExactly("/api/v2/**");
    }
  }

  @Test
  void v2GroupDocumentsPublicAccessSignupAdminAndWorkerSecuritySeparately() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(OpenApiConfig.class)) {
      GroupedOpenApi v2 = group(context.getBeansOfType(GroupedOpenApi.class), "v2");
      OpenAPI openApi = representativeV2OpenApi();

      v2.getOpenApiCustomizers().forEach(customizer -> customizer.customise(openApi));

      assertThat(securityNames(operation(openApi, "/api/v2/web/popup/random"))).isEmpty();
      assertThat(securityNames(operation(openApi, "/api/v2/auth/kakao/mobile/login"))).isEmpty();
      assertThat(securityNames(operation(openApi, "/api/v2/auth/refresh"))).isEmpty();
      assertThat(securityNames(operation(openApi, "/api/v2/test-auth/token")))
          .containsExactly("qaApiKeyAuth");
      assertThat(securityNames(operation(openApi, "/api/v2/auth/kakao/signup")))
          .containsExactly("bearerSignupAuth");
      assertThat(securityNames(operation(openApi, "/api/v2/popup")))
          .containsExactly("bearerAccessAuth");
      assertThat(securityNames(operation(openApi, "/api/v2/admin/popup-submissions")))
          .containsExactly("bearerAccessAuth");
      assertThat(securityNames(operation(openApi, "/api/v2/internal/popup")))
          .containsExactly("workerApiKeyAuth");
    }
  }

  @Test
  void declaresOnlyPurposeSpecificSchemesWithoutPuttingSecretsInTheDocument() {
    OpenAPI openApi = new OpenApiConfig().customOpenAPI();

    var schemes = openApi.getComponents().getSecuritySchemes();
    assertThat(schemes)
        .containsKeys("bearerAccessAuth", "bearerSignupAuth", "qaApiKeyAuth")
        .doesNotContainKey("bearerAuth");
    assertThat(openApi.getSecurity()).isNullOrEmpty();
    assertThat(schemes.get("qaApiKeyAuth").getType().toString()).isEqualTo("apiKey");
    assertThat(schemes.get("qaApiKeyAuth").getName()).isEqualTo("X-QA-Api-Key");
    assertThat(schemes.get("qaApiKeyAuth").getIn().toString()).isEqualTo("header");
    assertThat(schemes.get("workerApiKeyAuth").getType().toString()).isEqualTo("apiKey");
    assertThat(schemes.get("workerApiKeyAuth").getName()).isEqualTo("X-Worker-Api-Key");
    assertThat(schemes.get("workerApiKeyAuth").getIn().toString()).isEqualTo("header");
    assertThat(openApi.toString())
        .doesNotContain("internal.worker.api-key", "qa.auth.api-key", "jwt.secret");
  }

  private GroupedOpenApi group(Map<String, GroupedOpenApi> groups, String groupName) {
    return groups.values().stream()
        .filter(group -> groupName.equals(group.getGroup()))
        .findFirst()
        .orElseThrow();
  }

  private OpenAPI representativeV2OpenApi() {
    return new OpenAPI()
        .path("/api/v2/web/popup/random", new PathItem().get(new Operation()))
        .path("/api/v2/auth/kakao/mobile/login", new PathItem().post(new Operation()))
        .path("/api/v2/auth/refresh", new PathItem().post(new Operation()))
        .path("/api/v2/test-auth/token", new PathItem().post(new Operation()))
        .path("/api/v2/auth/kakao/signup", new PathItem().post(new Operation()))
        .path("/api/v2/popup", new PathItem().get(new Operation()))
        .path("/api/v2/admin/popup-submissions", new PathItem().get(new Operation()))
        .path("/api/v2/internal/popup", new PathItem().post(new Operation()));
  }

  private Operation operation(OpenAPI openApi, String path) {
    return openApi.getPaths().get(path).readOperations().get(0);
  }

  private java.util.List<String> securityNames(Operation operation) {
    if (operation.getSecurity() == null) {
      return java.util.List.of("INHERITED_GLOBAL_SECURITY");
    }
    return operation.getSecurity().stream()
        .flatMap(requirement -> requirement.keySet().stream())
        .toList();
  }
}
