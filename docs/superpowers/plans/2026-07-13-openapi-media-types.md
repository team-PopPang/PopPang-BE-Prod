# OpenAPI Media Type 정합성 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실제 JSON 응답을 반환하는 PopPang controller mapping에 `application/json`을 명시해 OpenAPI response의 부정확한 `*/*`를 제거하고 기존 API 계약을 보존한다.

**Architecture:** Production controller의 Spring MVC mapping을 media type의 source of truth로 사용한다. DB·Redis 없는 최소 Springdoc test application에서 production controller를 불러 `/v3/api-docs`를 생성하고, OpenAPI 계약과 MockMvc의 실제 JSON Content-Type을 함께 검증한다.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring MVC, springdoc-openapi 2.7.0, JUnit 5, MockMvc, Mockito, Jackson, AssertJ, Gradle 8.14.3, Spotless.

## Global Constraints

- `main` merge, push, 배포를 수행하지 않는다.
- API endpoint, response DTO, payload, status code, 인증·인가, 비즈니스 로직을 변경하지 않는다.
- 새 dependency, infrastructure, secret, DB schema 또는 DB 데이터를 추가·변경하지 않는다.
- 운영 API에는 GET 이외의 요청을 보내지 않는다.
- JSON response mapping에만 `produces = MediaType.APPLICATION_JSON_VALUE`를 추가한다.
- `ResponseEntity<Void>` mapping에는 produces를 추가하거나 OpenAPI content를 강제하지 않는다.
- JSON request 12개와 multipart request 2개의 기존 media type을 유지한다.
- binary, image, SSE, streaming, redirect response가 새로 존재한다고 가정하지 않는다.
- random/detail의 schema `$ref`를 각각 `#/components/schemas/ApiResponseListPopupWebRandomResponseDto`, `#/components/schemas/ApiResponsePopupWebDetailResponseDto`로 유지한다.
- 커밋 직전에 대상 파일과 메시지를 사용자에게 알리고 매번 별도 승인을 받는다.

---

### Task 1: 실제 `/v3/api-docs`를 검사하는 regression test 추가

**Files:**

- Create: `src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java`
- Reference: `docs/superpowers/specs/2026-07-13-openapi-media-types-design.md`
- Reference: `src/main/java/com/poppang/be/common/config/OpenApiConfig.java`
- Reference: 모든 `src/main/java/com/poppang/be/domain/**/presentation/**/*Controller.java`

**Interfaces:**

- Consumes: Production controller mapping과 springdoc이 생성하는 `/v3/api-docs` JSON.
- Produces: `build/openapi/openapi.json`, response/request media type 회귀 검증, 네 web popup GET의 schema 및 실제 Content-Type 검증.

- [ ] **Step 1: 최소 Springdoc test application과 계약 테스트 작성**

`src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java`를 다음 내용으로 생성한다.

```java
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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
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

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "post", "put", "patch", "delete");

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
    assertThat(jsonResponseCount).isEqualTo(44);
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
    assertJsonResponse("/api/v1/web/popup/favorite", null);
    assertJsonResponse("/api/v1/web/popup/upcoming", null);
  }

  @Test
  void webPopupRuntimeResponsesAreJson() throws Exception {
    given(popupWebService.getRandomPopupList()).willReturn(List.of());
    given(popupWebService.getFavoritePopupList()).willReturn(List.of());
    given(popupWebService.getUpcomingPopupList()).willReturn(List.of());
    given(popupWebService.getPopupDetail("popup-uuid"))
        .willReturn(PopupWebDetailResponseDto.builder().build());

    for (String path :
        List.of(
            "/api/v1/web/popup/random",
            "/api/v1/web/popup/favorite",
            "/api/v1/web/popup/upcoming",
            "/api/v1/web/popup/popup-uuid")) {
      mockMvc
          .perform(get(path).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
  }

  private void assertJsonResponse(String path, String expectedSchemaReference) {
    JsonNode contentNode = openApi.path("paths").path(path).path("get").path("responses").path("200").path("content");

    assertThat(fieldNames(contentNode)).containsExactly(MediaType.APPLICATION_JSON_VALUE);
    if (expectedSchemaReference != null) {
      assertThat(contentNode.path(MediaType.APPLICATION_JSON_VALUE).path("schema").path("$ref").asText())
          .isEqualTo(expectedSchemaReference);
    }
  }

  private List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
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
```

- [ ] **Step 2: Spotless로 테스트 파일 포맷 적용**

Run: `./gradlew spotlessApply`

Expected: 테스트 파일이 google-java-format 1.17.0 형식으로 정리된다.

- [ ] **Step 3: RED 실행과 실패 원인 확인**

Run:

```bash
./gradlew test --tests com.poppang.be.common.config.OpenApiMediaTypeContractTest --rerun-tasks
```

Expected: `responseContentsUseApplicationJsonAndBodylessResponsesHaveNoContent`와 `webPopupResponsesKeepJsonMediaTypeAndSchemaReferences`가 실패한다. 실패 내용에는 response wildcard 위치 또는 기대한 `application/json` 대신 현재 `*/*`가 있다는 차이가 나타나야 한다. request body와 body 없는 response, 실제 MockMvc Content-Type 검증은 통과해야 한다.

- [ ] **Step 4: 수정 전 OpenAPI snapshot 보존**

Run: `cp build/openapi/openapi.json /tmp/poppang-openapi-before-media-type-fix.json`

Expected: `/tmp/poppang-openapi-before-media-type-fix.json`의 response content에 `*/*` 44개가 존재한다.

Run:

```bash
jq '[.paths[][]?.responses? // {} | .[]?.content? // {} | keys[] | select(. == "*/*")] | length' /tmp/poppang-openapi-before-media-type-fix.json
```

Expected: `44`

---

### Task 2: JSON response mapping에 정확한 produces 선언

**Files:**

- Modify: `src/main/java/com/poppang/be/domain/recommend/presentation/RecommendController.java`
- Modify: `src/main/java/com/poppang/be/domain/auth/presentation/AuthController.java`
- Modify: `src/main/java/com/poppang/be/domain/auth/presentation/TokenController.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/presentation/web/PopupWebController.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/presentation/app/PopupUserController.java`
- Modify: `src/main/java/com/poppang/be/domain/keyword/presentation/UserAlertKeywordController.java`
- Modify: `src/main/java/com/poppang/be/domain/favorite/presentation/UserFavoriteController.java`
- Modify: `src/main/java/com/poppang/be/domain/alert/presentation/UserAlertController.java`
- Modify: `src/main/java/com/poppang/be/domain/users/presentation/UsersController.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/presentation/app/PopupTotalViewController.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/presentation/app/PopupAdminController.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/presentation/app/PopupController.java`
- Do not modify: `PopupImageController.java`, `PopupSubmissionController.java`

**Interfaces:**

- Consumes: Task 1이 고정한 기존 path, method, status, DTO schema와 media type 기대값.
- Produces: JSON body response만 `application/json`으로 선언된 Spring MVC/OpenAPI 계약.

- [ ] **Step 1: 모든 endpoint가 JSON body를 반환하는 controller에는 클래스 수준 produces 추가**

각 파일에 `org.springframework.http.MediaType` import를 추가하고 기존 `@RequestMapping`을 다음과 같이 바꾼다.

```java
// RecommendController.java
@RequestMapping(value = "/api/v1/recommend", produces = MediaType.APPLICATION_JSON_VALUE)

// AuthController.java
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)

// TokenController.java
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)

// PopupWebController.java
@RequestMapping(value = "/api/v1/web/popup", produces = MediaType.APPLICATION_JSON_VALUE)

// PopupUserController.java
@RequestMapping(
    value = "/api/v1/users/{userUuid}/popups",
    produces = MediaType.APPLICATION_JSON_VALUE)
```

Auth/Token endpoint는 OpenAPI에서 hidden이지만 실제 JSON response 계약을 동일하게 명시한다.

- [ ] **Step 2: body 없는 response와 섞인 controller에는 JSON body 메서드만 produces 추가**

각 파일에 `org.springframework.http.MediaType` import를 추가한다. `PopupAdminController`는 기존 import를 재사용한다. 다음 annotation만 교체하고 `ResponseEntity<Void>` 메서드의 annotation은 변경하지 않는다.

```java
// UserAlertKeywordController.java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)

// UserFavoriteController.java
@GetMapping(value = "/count/{popupUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/popup/{userUuid}", produces = MediaType.APPLICATION_JSON_VALUE)

// UserAlertController.java
@GetMapping(value = "/popups", produces = MediaType.APPLICATION_JSON_VALUE)

// UsersController.java
@GetMapping(value = "/{userUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
@PatchMapping(value = "{userUuid}/alert-status", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/nickname/duplicated", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(
    value = "/{userUuid}/fcm-token/duplicate-check",
    produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/with-alert-keyword/a", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/with-alert-keyword/b", produces = MediaType.APPLICATION_JSON_VALUE)

// PopupTotalViewController.java
@GetMapping(
    value = "/{popupUuid}/total-view-count", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/{popupUuid}/view-count", produces = MediaType.APPLICATION_JSON_VALUE)

// PopupAdminController.java
@GetMapping(value = "/popup-submissions", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(
    value = "/popup-submissions/{popupSubmissionId}",
    produces = MediaType.APPLICATION_JSON_VALUE)
@PutMapping(
    value = "/popup-submissions/{popupSubmissionId}",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE)

// PopupController.java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/{popupUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/upcoming", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/{userUuid}/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/inProgress", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/regions/districts", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/filtered", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/filtered/home", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/filtered/map", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/{popupUuid}/related", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(value = "/random", produces = MediaType.APPLICATION_JSON_VALUE)
@GetMapping(
    value = "/recommendations/{recommendId}", produces = MediaType.APPLICATION_JSON_VALUE)
```

- [ ] **Step 3: Production 코드 포맷 적용**

Run: `./gradlew spotlessApply`

Expected: import 정리와 mapping annotation 줄바꿈이 google-java-format 규칙에 맞게 정리된다.

- [ ] **Step 4: 같은 regression test로 GREEN 확인**

Run:

```bash
./gradlew test --tests com.poppang.be.common.config.OpenApiMediaTypeContractTest --rerun-tasks
```

Expected: 4개 테스트가 모두 PASS하고 `build/openapi/openapi.json`이 새로 생성된다.

- [ ] **Step 5: 생성된 OpenAPI의 media type과 schema 확인**

Run:

```bash
jq '[.paths[][]?.responses? // {} | .[]?.content? // {} | keys[] | select(. == "*/*")] | length' build/openapi/openapi.json
jq '{random: .paths["/api/v1/web/popup/random"].get.responses["200"].content, detail: .paths["/api/v1/web/popup/{popupUuid}"].get.responses["200"].content, favorite: .paths["/api/v1/web/popup/favorite"].get.responses["200"].content, upcoming: .paths["/api/v1/web/popup/upcoming"].get.responses["200"].content}' build/openapi/openapi.json
```

Expected: 첫 명령은 `0`. 네 response는 `application/json`만 가지며 random/detail `$ref`는 Global Constraints의 기존 이름과 동일하다.

- [ ] **Step 6: 의도된 media type 외 OpenAPI 계약 차이가 없는지 비교**

Run:

```bash
diff -u \
  <(jq -S 'walk(if type == "object" and has("content") then .content |= with_entries(if (.key == "*/*" or .key == "application/json") then .key = "__JSON__" else . end) else . end)' /tmp/poppang-openapi-before-media-type-fix.json) \
  <(jq -S 'walk(if type == "object" and has("content") then .content |= with_entries(if (.key == "*/*" or .key == "application/json") then .key = "__JSON__" else . end) else . end)' build/openapi/openapi.json)
```

Expected: 출력 없음, exit code 0. path, method, status, payload schema, security가 동일하고 media type key만 바뀐다.

---

### Task 3: 전체 검증과 merge 가능한 상태 확인

**Files:**

- Verify: Task 1과 Task 2의 모든 변경 파일
- Verify: `build/openapi/openapi.json`
- Do not modify: deployment, CI, application private config, DB 관련 파일

**Interfaces:**

- Consumes: GREEN 상태의 regression test와 production controller mapping.
- Produces: 전체 build/format 증거, 변경 범위 audit, 사용자 merge 후 read-only 검증 절차.

- [ ] **Step 1: 전체 테스트 강제 재실행**

Run: `./gradlew test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`, 실패 테스트 0개.

- [ ] **Step 2: compile과 전체 build 실행**

Run:

```bash
./gradlew compileJava
./gradlew build
```

Expected: 두 명령 모두 `BUILD SUCCESSFUL`.

- [ ] **Step 3: format/lint 검사 실행**

Run: `./gradlew spotlessCheck`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 최종 OpenAPI 분류 재확인**

Run:

```bash
jq -r '[.paths | to_entries[] as $p | $p.value | to_entries[] | select(.key | IN("get", "post", "put", "patch", "delete")) as $o | $o.value.responses // {} | to_entries[] as $r | ($r.value.content // {}) | keys[] | {path:$p.key, method:$o.key, status:$r.key, mediaType:.}] | group_by(.mediaType) | map({mediaType:.[0].mediaType, count:length})' build/openapi/openapi.json
jq -r '[.paths | to_entries[] as $p | $p.value | to_entries[] | select(.key | IN("get", "post", "put", "patch", "delete")) as $o | ($o.value.requestBody.content // {}) | keys[] | {path:$p.key, method:$o.key, mediaType:.}] | group_by(.mediaType) | map({mediaType:.[0].mediaType, count:length})' build/openapi/openapi.json
```

Expected: response content는 `application/json` 44개만 존재한다. request body는 `application/json` 12개, `multipart/form-data` 2개다. response `*/*`는 0개다.

- [ ] **Step 5: source 변경이 mapping media type과 테스트에 한정됐는지 확인**

Run:

```bash
git diff --check
git diff --stat HEAD
git diff HEAD -- src/main/java src/test/java
git status --short
```

Expected: controller의 `MediaType` import와 mapping `produces`, 새 regression test만 변경되어 있다. endpoint 문자열, method signature, DTO, status, security annotation, service 호출은 바뀌지 않는다.

- [ ] **Step 6: 구현 커밋 승인 요청**

사용자에게 실제 `git status --short`의 대상 파일 목록과 다음 예정 메시지를 알리고 명시적으로 승인받는다.

```text
fix: OpenAPI JSON response media type 명시
```

승인 전에는 `git add`나 `git commit`을 실행하지 않는다.

- [ ] **Step 7: 승인된 경우에만 구현 파일 커밋**

Run:

```bash
git add src/main/java/com/poppang/be/domain/alert/presentation/UserAlertController.java \
  src/main/java/com/poppang/be/domain/auth/presentation/AuthController.java \
  src/main/java/com/poppang/be/domain/auth/presentation/TokenController.java \
  src/main/java/com/poppang/be/domain/favorite/presentation/UserFavoriteController.java \
  src/main/java/com/poppang/be/domain/keyword/presentation/UserAlertKeywordController.java \
  src/main/java/com/poppang/be/domain/popup/presentation/app/PopupAdminController.java \
  src/main/java/com/poppang/be/domain/popup/presentation/app/PopupController.java \
  src/main/java/com/poppang/be/domain/popup/presentation/app/PopupTotalViewController.java \
  src/main/java/com/poppang/be/domain/popup/presentation/app/PopupUserController.java \
  src/main/java/com/poppang/be/domain/popup/presentation/web/PopupWebController.java \
  src/main/java/com/poppang/be/domain/recommend/presentation/RecommendController.java \
  src/main/java/com/poppang/be/domain/users/presentation/UsersController.java \
  src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java \
  docs/superpowers/plans/2026-07-13-openapi-media-types.md
git commit -m "fix: OpenAPI JSON response media type 명시"
```

Expected: 구현, regression test와 이 실행 계획이 한 커밋에 포함된다.

- [ ] **Step 8: merge/push/deploy 없이 최종 보고**

최종 보고에는 근본 원인, 수정 파일, RED/GREEN 출력, 전체 test/build/Spotless 결과, 네 web popup media type과 schema `$ref`, `*/*` 잔여 목록, MockMvc 실제 Content-Type, 계약 비교 결과, 사용자의 merge 후 GET-only 검증 명령, 남은 위험을 포함한다.

`git push`, `main` merge, GitHub Actions 실행, make 배포 명령은 실행하지 않는다.
