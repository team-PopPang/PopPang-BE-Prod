package com.poppang.be.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.domain.alert.application.UserAlertService;
import com.poppang.be.domain.alert.presentation.UserAlertController;
import com.poppang.be.domain.favorite.application.UserFavoriteService;
import com.poppang.be.domain.favorite.presentation.UserFavoriteController;
import com.poppang.be.domain.keyword.application.UserAlertKeywordService;
import com.poppang.be.domain.keyword.presentation.UserAlertKeywordController;
import com.poppang.be.domain.popup.application.PopupAdminService;
import com.poppang.be.domain.popup.application.PopupImageService;
import com.poppang.be.domain.popup.application.PopupService;
import com.poppang.be.domain.popup.application.PopupSubmissionService;
import com.poppang.be.domain.popup.application.PopupTotalViewCountService;
import com.poppang.be.domain.popup.application.PopupUserService;
import com.poppang.be.domain.popup.application.PopupWebService;
import com.poppang.be.domain.popup.dto.web.response.PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.presentation.app.PopupAdminController;
import com.poppang.be.domain.popup.presentation.app.PopupController;
import com.poppang.be.domain.popup.presentation.app.PopupImageController;
import com.poppang.be.domain.popup.presentation.app.PopupSubmissionController;
import com.poppang.be.domain.popup.presentation.app.PopupTotalViewController;
import com.poppang.be.domain.popup.presentation.app.PopupUserController;
import com.poppang.be.domain.popup.presentation.web.PopupWebController;
import com.poppang.be.domain.recommend.application.RecommendService;
import com.poppang.be.domain.recommend.presentation.RecommendController;
import com.poppang.be.domain.users.application.UsersService;
import com.poppang.be.domain.users.presentation.UsersController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = OpenApiMediaTypeContractTest.TestApplication.class,
    properties = {
      "springdoc.api-docs.enabled=true",
      "springdoc.api-docs.path=/v3/api-docs",
      "springdoc.swagger-ui.enabled=false"
    })
@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiMediaTypeContractTest {

  private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UserAlertKeywordService userAlertKeywordService;
  @MockitoBean private UserFavoriteService userFavoriteService;
  @MockitoBean private UserAlertService userAlertService;
  @MockitoBean private RecommendService recommendService;
  @MockitoBean private UsersService usersService;
  @MockitoBean private PopupWebService popupWebService;
  @MockitoBean private PopupTotalViewCountService popupTotalViewCountService;
  @MockitoBean private PopupUserService popupUserService;
  @MockitoBean private PopupAdminService popupAdminService;
  @MockitoBean private PopupService popupService;
  @MockitoBean private PopupImageService popupImageService;
  @MockitoBean private PopupSubmissionService popupSubmissionService;

  private JsonNode openApi;

  @BeforeAll
  void loadOpenApi() throws Exception {
    String openApiJson =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    openApi = objectMapper.readTree(openApiJson);

    Path output = Path.of("build", "openapi", "openapi.json");
    Files.createDirectories(output.getParent());
    Files.writeString(output, openApiJson);
  }

  @Test
  void responseContentsUseApplicationJsonAndBodylessResponsesHaveNoContent() {
    List<String> wildcardLocations = new ArrayList<>();
    List<String> unexpectedMediaTypes = new ArrayList<>();
    int jsonResponseCount = 0;
    int bodylessResponseCount = 0;

    var paths = openApi.path("paths").fields();
    while (paths.hasNext()) {
      var path = paths.next();
      var operations = path.getValue().fields();
      while (operations.hasNext()) {
        var operation = operations.next();
        if (!HTTP_METHODS.contains(operation.getKey())) {
          continue;
        }

        var responses = operation.getValue().path("responses").fields();
        while (responses.hasNext()) {
          var response = responses.next();
          String location =
              operation.getKey().toUpperCase() + " " + path.getKey() + " " + response.getKey();
          JsonNode contentNode = response.getValue().get("content");
          if (contentNode == null || contentNode.isEmpty()) {
            bodylessResponseCount++;
            continue;
          }

          for (String mediaType : fieldNames(contentNode)) {
            if (MediaType.ALL_VALUE.equals(mediaType)) {
              wildcardLocations.add(location);
            } else if (MediaType.APPLICATION_JSON_VALUE.equals(mediaType)) {
              jsonResponseCount++;
            } else {
              unexpectedMediaTypes.add(location + " -> " + mediaType);
            }
          }
        }
      }
    }

    assertThat(wildcardLocations).isEmpty();
    assertThat(unexpectedMediaTypes).isEmpty();
    assertThat(jsonResponseCount).isEqualTo(45);
    assertThat(bodylessResponseCount).isEqualTo(16);
  }

  @Test
  void requestBodiesKeepJsonAndMultipartMediaTypes() {
    List<String> unexpectedMediaTypes = new ArrayList<>();
    Set<String> multipartLocations = new TreeSet<>();
    int jsonRequestCount = 0;

    var paths = openApi.path("paths").fields();
    while (paths.hasNext()) {
      var path = paths.next();
      var operations = path.getValue().fields();
      while (operations.hasNext()) {
        var operation = operations.next();
        if (!HTTP_METHODS.contains(operation.getKey())) {
          continue;
        }

        JsonNode requestBody = operation.getValue().get("requestBody");
        if (requestBody == null) {
          continue;
        }

        String location = operation.getKey().toUpperCase() + " " + path.getKey();
        for (String mediaType : fieldNames(requestBody.path("content"))) {
          if (MediaType.APPLICATION_JSON_VALUE.equals(mediaType)) {
            jsonRequestCount++;
          } else if (MediaType.MULTIPART_FORM_DATA_VALUE.equals(mediaType)) {
            multipartLocations.add(location);
          } else {
            unexpectedMediaTypes.add(location + " -> " + mediaType);
          }
        }
      }
    }

    assertThat(unexpectedMediaTypes).isEmpty();
    assertThat(jsonRequestCount).isEqualTo(12);
    assertThat(multipartLocations)
        .containsExactly(
            "POST /api/v1/popup-submissions",
            "PUT /api/v1/admin/popup-submissions/{popupSubmissionId}");
  }

  @Test
  void webPopupResponsesKeepJsonMediaTypeAndSchemaReferences() {
    assertJsonResponse(
        "/api/v1/web/popup/random",
        "#/components/schemas/ApiResponseListPopupWebRandomResponseDto");
    assertJsonResponse(
        "/api/v1/web/popup/{popupUuid}",
        "#/components/schemas/ApiResponsePopupWebDetailResponseDto");
    assertJsonResponse(
        "/api/v1/web/popup/favorite",
        "#/components/schemas/ApiResponseListPopupWebFavoriteResponseDto");
    assertJsonResponse(
        "/api/v1/web/popup/in-progress",
        "#/components/schemas/ApiResponseListPopupWebInProgressResponseDto");
    assertJsonResponse(
        "/api/v1/web/popup/upcoming",
        "#/components/schemas/ApiResponseListPopupWebUpcomingResponseDto");
  }

  @Test
  void inProgressWebPopupOpenApiContractIsExact() {
    JsonNode operation = openApi.path("paths").path("/api/v1/web/popup/in-progress").path("get");

    assertThat(operation.path("tags").isArray()).isTrue();
    assertThat(operation.path("tags").size()).isEqualTo(1);
    assertThat(operation.path("tags").path(0).asText()).isEqualTo("[WEB] [POPUP]");
    assertThat(operation.path("summary").asText()).isEqualTo("[WEB] 현재 진행 중인 팝업 목록 조회");
    assertThat(operation.path("description").asText())
        .isEqualTo("현재 날짜를 기준으로 운영 중인 팝업스토어 목록을 조회합니다.");
    assertThat(operation.path("operationId").asText()).isEqualTo("getWebInProgressPopupList");
    assertThat(operation.has("parameters")).isFalse();
    assertThat(operation.has("requestBody")).isFalse();

    JsonNode properties =
        openApi
            .path("components")
            .path("schemas")
            .path("PopupWebInProgressResponseDto")
            .path("properties");
    assertThat(fieldNames(properties))
        .containsExactly("popupUuid", "name", "thumbnailUrl", "region", "startDate", "endDate");
    assertStringSchema(properties.path("popupUuid"), null);
    assertStringSchema(properties.path("name"), null);
    assertStringSchema(properties.path("thumbnailUrl"), null);
    assertStringSchema(properties.path("region"), null);
    assertStringSchema(properties.path("startDate"), "date");
    assertStringSchema(properties.path("endDate"), "date");
  }

  @Test
  void webPopupRuntimeResponsesAreJson() throws Exception {
    given(popupWebService.getRandomPopupList()).willReturn(List.of());
    given(popupWebService.getFavoritePopupList()).willReturn(List.of());
    given(popupWebService.getInProgressPopupList()).willReturn(List.of());
    given(popupWebService.getUpcomingPopupList()).willReturn(List.of());
    given(popupWebService.getPopupDetail("popup-uuid"))
        .willReturn(PopupWebDetailResponseDto.builder().build());

    for (String path :
        List.of(
            "/api/v1/web/popup/random",
            "/api/v1/web/popup/favorite",
            "/api/v1/web/popup/in-progress",
            "/api/v1/web/popup/upcoming",
            "/api/v1/web/popup/popup-uuid")) {
      mockMvc
          .perform(get(path).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
  }

  private void assertJsonResponse(String path, String expectedSchemaReference) {
    JsonNode contentNode =
        openApi.path("paths").path(path).path("get").path("responses").path("200").path("content");

    assertThat(fieldNames(contentNode)).containsExactly(MediaType.APPLICATION_JSON_VALUE);
    if (expectedSchemaReference != null) {
      assertThat(
              contentNode
                  .path(MediaType.APPLICATION_JSON_VALUE)
                  .path("schema")
                  .path("$ref")
                  .asText())
          .isEqualTo(expectedSchemaReference);
    }
  }

  private List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private void assertStringSchema(JsonNode schema, String expectedFormat) {
    assertThat(schema.path("type").asText()).isEqualTo("string");
    if (expectedFormat == null) {
      assertThat(schema.has("format")).isFalse();
    } else {
      assertThat(schema.path("format").asText()).isEqualTo(expectedFormat);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
      })
  @Import({
    OpenApiConfig.class,
    UserAlertKeywordController.class,
    UserFavoriteController.class,
    UserAlertController.class,
    RecommendController.class,
    UsersController.class,
    PopupWebController.class,
    PopupTotalViewController.class,
    PopupUserController.class,
    PopupAdminController.class,
    PopupController.class,
    PopupImageController.class,
    PopupSubmissionController.class
  })
  static class TestApplication {}
}
