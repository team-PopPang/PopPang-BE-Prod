package com.poppang.be.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.exception.GlobalExceptionHandler;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.domain.alert.application.V2InternalUserAlertService;
import com.poppang.be.domain.alert.presentation.v2.V2InternalUserAlertController;
import com.poppang.be.domain.popup.application.V2InternalPopupService;
import com.poppang.be.domain.popup.presentation.v2.V2InternalPopupController;
import com.poppang.be.domain.users.application.V2InternalUsersService;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import com.poppang.be.domain.users.presentation.v2.V2InternalUsersController;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = {
      V2InternalPopupController.class,
      V2InternalUsersController.class,
      V2InternalUserAlertController.class
    },
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2InternalWorkerControllerTest {

  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2InternalPopupService popupService;
  @MockitoBean private V2InternalUsersService usersService;
  @MockitoBean private V2InternalUserAlertService userAlertService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @DynamicPropertySource
  static void workerApiKey(DynamicPropertyRegistry registry) {
    registry.add("internal.worker.api-key", () -> WORKER_API_KEY);
  }

  @Test
  void everyInternalMappingRejectsMissingBlankAndWrongKeys() throws Exception {
    for (MockHttpServletRequestBuilder request : allFiveRequests()) {
      mockMvc
          .perform(request)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_WORKER_API_KEY.getCode()));
    }
    for (MockHttpServletRequestBuilder request : allFiveRequests()) {
      mockMvc.perform(request.header("X-Worker-Api-Key", "")).andExpect(status().isUnauthorized());
    }
    for (MockHttpServletRequestBuilder request : allFiveRequests()) {
      mockMvc
          .perform(request.header("X-Worker-Api-Key", "wrong-worker-key"))
          .andExpect(status().isUnauthorized());
    }

    verifyNoInteractions(popupService, usersService, userAlertService);
  }

  @Test
  void everyInternalMappingAllowsTheConfiguredWorkerKey() throws Exception {
    for (MockHttpServletRequestBuilder request : allFiveRequests()) {
      mockMvc
          .perform(request.header("X-Worker-Api-Key", WORKER_API_KEY))
          .andExpect(status().isOk());
    }

    verify(popupService).registerPopup(org.mockito.ArgumentMatchers.any());
    verify(popupService)
        .upsertImages(
            org.mockito.ArgumentMatchers.eq("popup-uuid"), org.mockito.ArgumentMatchers.anyList());
    verify(usersService).getUsersWithAlertKeyword();
    verify(usersService).getUsersWithAlertKeywordGroup();
    verify(userAlertService)
        .registerUserAlert(
            org.mockito.ArgumentMatchers.eq("recipient-uuid"), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void jwtAloneNeverAuthenticatesAnInternalRequest() throws Exception {
    for (String token : List.of("access-token", "admin-token", "signup-token", "refresh-token")) {
      mockMvc
          .perform(
              get("/api/v2/internal/user/with-alert-keyword/a")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_WORKER_API_KEY.getCode()));
    }

    verifyNoInteractions(jwtProvider, usersService);
  }

  @Test
  void keyInQueryPathOrBodyNeverAuthenticates() throws Exception {
    mockMvc
        .perform(
            get("/api/v2/internal/user/with-alert-keyword/a")
                .queryParam("X-Worker-Api-Key", WORKER_API_KEY))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v2/internal/users/{userUuid}/alert", WORKER_API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"popupUuid\":\"popup-uuid\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v2/internal/popup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + WORKER_API_KEY + "\"}"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(popupService, usersService, userAlertService);
  }

  @Test
  void rejectedKeyIsNeverReflectedInTheErrorResponse() throws Exception {
    String rejectedSecret = "rejected-" + UUID.randomUUID();

    String response =
        mockMvc
            .perform(
                get("/api/v2/internal/user/with-alert-keyword/a")
                    .header("X-Worker-Api-Key", rejectedSecret))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).doesNotContain(rejectedSecret).doesNotContain(WORKER_API_KEY);
  }

  private List<MockHttpServletRequestBuilder> allFiveRequests() {
    return List.of(
        post("/api/v2/internal/popup").contentType(MediaType.APPLICATION_JSON).content("{}"),
        put("/api/v2/internal/popup/{popupUuid}/images", "popup-uuid")
            .contentType(MediaType.APPLICATION_JSON)
            .content("[{}]"),
        get("/api/v2/internal/user/with-alert-keyword/a"),
        get("/api/v2/internal/user/with-alert-keyword/b"),
        post("/api/v2/internal/users/{userUuid}/alert", "recipient-uuid")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"popupUuid\":\"popup-uuid\"}"));
  }
}
