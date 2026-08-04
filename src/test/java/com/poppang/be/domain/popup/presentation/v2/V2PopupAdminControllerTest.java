package com.poppang.be.domain.popup.presentation.v2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.exception.GlobalExceptionHandler;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.popup.application.V2PopupAdminService;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2PopupAdminController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2PopupAdminControllerTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String POPUP_UUID = "popup-uuid";
  private static final Long SUBMISSION_ID = 15L;
  private static final String ACCESS_TOKEN = "access.token";
  private static final String SIGNUP_TOKEN = "signup.token";
  private static final String REFRESH_TOKEN = "refresh.token";
  private static final String INVALID_TOKEN = "invalid.token";
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2PopupAdminService popupAdminService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @DynamicPropertySource
  static void workerApiKey(DynamicPropertyRegistry registry) {
    registry.add("internal.worker.api-key", () -> WORKER_API_KEY);
  }

  @BeforeEach
  void setUpAuthentication() {
    given(jwtProvider.verify(ACCESS_TOKEN)).willReturn(jwt(JwtTokenType.ACCESS));
    given(jwtProvider.verify(SIGNUP_TOKEN)).willReturn(jwt(JwtTokenType.SIGNUP));
    given(jwtProvider.verify(REFRESH_TOKEN)).willReturn(jwt(JwtTokenType.REFRESH));
    given(jwtProvider.verify(INVALID_TOKEN)).willThrow(new BaseException(ErrorCode.INVALID_TOKEN));
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user(Role.MEMBER)));
    given(popupAdminService.getPopupSubmissions(any())).willReturn(List.of());
    given(popupAdminService.getPopupSubmissionDetail(SUBMISSION_ID))
        .willReturn(V2PopupSubmissionAdminDetailResponseDto.builder().build());
    given(popupAdminService.updatePopupSubmission(eq(SUBMISSION_ID), any(), isNull()))
        .willReturn(V2PopupSubmissionAdminUpdateResponseDto.from(POPUP_UUID));
  }

  @Test
  void everyMappingRejectsMemberAndAllowsCurrentAdmin() throws Exception {
    for (MockHttpServletRequestBuilder request : allFiveRequests()) {
      mockMvc.perform(withToken(request, ACCESS_TOKEN)).andExpect(status().isForbidden());
    }
    verifyNoInteractions(popupAdminService);

    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user(Role.ADMIN)));

    for (MockHttpServletRequestBuilder request : allFiveRequests()) {
      mockMvc.perform(withToken(request, ACCESS_TOKEN)).andExpect(status().isOk());
    }

    verify(popupAdminService).deactivatePopup(POPUP_UUID);
    verify(popupAdminService).getPopupSubmissions("전체");
    verify(popupAdminService).getPopupSubmissionDetail(SUBMISSION_ID);
    verify(popupAdminService)
        .updatePopupSubmission(
            eq(SUBMISSION_ID), any(V2PopupSubmissionAdminUpdateRequestDto.class), isNull());
    verify(popupAdminService)
        .updateSubmissionStatus(
            eq(SUBMISSION_ID), any(V2PopupSubmissionStatusUpdateRequestDto.class));
  }

  @Test
  void tokenIsRequiredAndInvalidTokenReturnsUnauthorizedJson() throws Exception {
    mockMvc.perform(get(submissionListPath())).andExpect(status().isUnauthorized());
    mockMvc
        .perform(withToken(get(submissionListPath()), INVALID_TOKEN))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.getCode()));

    verifyNoInteractions(popupAdminService);
  }

  @Test
  void signupAndRefreshTokensCannotUseAdminMappings() throws Exception {
    mockMvc
        .perform(withToken(get(submissionListPath()), SIGNUP_TOKEN))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(withToken(get(submissionListPath()), REFRESH_TOKEN))
        .andExpect(status().isForbidden());

    verifyNoInteractions(popupAdminService);
  }

  @Test
  void deletedAndPendingUsersCannotUseAdminMappings() throws Exception {
    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(Optional.of(user(Role.ADMIN, SignupStatus.COMPLETED, true)));
    mockMvc
        .perform(withToken(get(submissionListPath()), ACCESS_TOKEN))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_NOT_ACTIVE.getCode()));

    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(Optional.of(user(Role.ADMIN, SignupStatus.PENDING, false)));
    mockMvc
        .perform(withToken(get(submissionListPath()), ACCESS_TOKEN))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));

    verifyNoInteractions(popupAdminService);
  }

  @Test
  void callerUuidQueryIsIgnoredAndDefaultStatusIsPreserved() throws Exception {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user(Role.ADMIN)));

    mockMvc
        .perform(
            withToken(get(submissionListPath()).queryParam("uuid", "attacker-uuid"), ACCESS_TOKEN))
        .andExpect(status().isOk());

    verify(popupAdminService).getPopupSubmissions("전체");
  }

  private List<MockHttpServletRequestBuilder> allFiveRequests() {
    return List.of(
        patch("/api/v2/admin/popup/{popupUuid}/deactivate", POPUP_UUID),
        get(submissionListPath()),
        get("/api/v2/admin/popup-submissions/{popupSubmissionId}", SUBMISSION_ID),
        approvalRequest(),
        patch("/api/v2/admin/popup-submissions/{submissionId}/status", SUBMISSION_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"popupSubmissionStatus\":\"REJECTED\"}"));
  }

  private MockHttpServletRequestBuilder approvalRequest() {
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            "{\"status\":\"REJECTED\"}".getBytes(StandardCharsets.UTF_8));
    return multipart(HttpMethod.PUT, "/api/v2/admin/popup-submissions/{id}", SUBMISSION_ID)
        .file(request);
  }

  private String submissionListPath() {
    return "/api/v2/admin/popup-submissions";
  }

  private MockHttpServletRequestBuilder withToken(
      MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private Users user(Role role) {
    return user(role, SignupStatus.COMPLETED, false);
  }

  private Users user(Role role, SignupStatus signupStatus, boolean deleted) {
    return Users.builder()
        .uuid(USER_UUID)
        .role(role)
        .signupStatus(signupStatus)
        .deleted(deleted)
        .build();
  }

  private VerifiedJwt jwt(JwtTokenType type) {
    return new VerifiedJwt(
        USER_UUID,
        type,
        type == JwtTokenType.SIGNUP ? "poppang-signup-v2" : "poppang-app-v2",
        Instant.parse("2026-08-04T00:00:00Z"),
        Instant.parse("2026-08-04T00:15:00Z"),
        type.name().toLowerCase() + "-jti",
        type == JwtTokenType.SIGNUP ? null : "session");
  }
}
