package com.poppang.be.domain.popup.presentation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.poppang.be.domain.popup.application.V2PopupSubmissionService;
import com.poppang.be.domain.popup.dto.v2.V2PopupSubmissionCreateRequestDto;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2PopupSubmissionController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2PopupSubmissionControllerTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String ACCESS_TOKEN = "access.token";
  private static final String SIGNUP_TOKEN = "signup.token";
  private static final String REFRESH_TOKEN = "refresh.token";
  private static final String INVALID_TOKEN = "invalid.token";
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2PopupSubmissionService popupSubmissionService;
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
    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(
            Optional.of(
                Users.builder()
                    .uuid(USER_UUID)
                    .role(Role.MEMBER)
                    .signupStatus(SignupStatus.COMPLETED)
                    .deleted(false)
                    .build()));
  }

  @Test
  void accessTokenUsesOnlyPrincipalUuidAndReturnsEmptyOkResponse() throws Exception {
    mockMvc
        .perform(withToken(validRequest(), ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());

    ArgumentCaptor<V2PopupSubmissionCreateRequestDto> requestCaptor =
        ArgumentCaptor.forClass(V2PopupSubmissionCreateRequestDto.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MultipartFile>> imagesCaptor = ArgumentCaptor.forClass(List.class);
    verify(popupSubmissionService)
        .createPopupSubmission(
            org.mockito.ArgumentMatchers.eq(USER_UUID),
            requestCaptor.capture(),
            imagesCaptor.capture());
    assertThat(requestCaptor.getValue().getName()).isEqualTo("테스트 팝업");
    assertThat(requestCaptor.getValue().getRecommendIdList()).containsExactly(1L, 2L);
    assertThat(imagesCaptor.getValue()).hasSize(1);
  }

  @Test
  void tokenIsRequiredAndInvalidTokenReturnsUnauthorizedJson() throws Exception {
    mockMvc.perform(validRequest()).andExpect(status().isUnauthorized());
    mockMvc
        .perform(withToken(validRequest(), INVALID_TOKEN))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.getCode()));

    verifyNoInteractions(popupSubmissionService);
  }

  @Test
  void signupAndRefreshTokensCannotCreateSubmission() throws Exception {
    mockMvc.perform(withToken(validRequest(), SIGNUP_TOKEN)).andExpect(status().isForbidden());
    mockMvc.perform(withToken(validRequest(), REFRESH_TOKEN)).andExpect(status().isForbidden());

    verifyNoInteractions(popupSubmissionService);
  }

  @Test
  void callerUuidPropertyCannotOverrideTheAuthenticatedPrincipal() throws Exception {
    MockMultipartFile request =
        jsonPart(
            """
            {
              "userUuid":"99999999-9999-9999-9999-999999999999",
              "name":"테스트 팝업",
              "startDate":"2026-08-01",
              "endDate":"2026-08-31",
              "roadAddress":"서울 성동구 왕십리로 123",
              "region":"서울",
              "description":"설명",
              "recommendIdList":[1]
            }
            """);

    mockMvc.perform(withToken(multipartRequest(request), ACCESS_TOKEN)).andExpect(status().isOk());

    verify(popupSubmissionService)
        .createPopupSubmission(
            org.mockito.ArgumentMatchers.eq(USER_UUID),
            org.mockito.ArgumentMatchers.any(V2PopupSubmissionCreateRequestDto.class),
            org.mockito.ArgumentMatchers.anyList());
  }

  private MockHttpServletRequestBuilder validRequest() {
    return multipartRequest(
        jsonPart(
            """
            {
              "name":"테스트 팝업",
              "startDate":"2026-08-01",
              "endDate":"2026-08-31",
              "roadAddress":"서울 성동구 왕십리로 123",
              "region":"서울",
              "description":"설명",
              "recommendIdList":[1,2]
            }
            """));
  }

  private MockHttpServletRequestBuilder multipartRequest(MockMultipartFile request) {
    return multipart("/api/v2/popup-submissions")
        .file(request)
        .file(new MockMultipartFile("images", "popup.jpg", "image/jpeg", new byte[] {1, 2, 3}));
  }

  private MockMultipartFile jsonPart(String json) {
    return new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
  }

  private MockHttpServletRequestBuilder withToken(
      MockHttpServletRequestBuilder request, String token) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private VerifiedJwt jwt(JwtTokenType type) {
    return new VerifiedJwt(
        USER_UUID,
        type,
        type == JwtTokenType.SIGNUP ? "poppang-signup-v2" : "poppang-app-v2",
        Instant.parse("2026-08-03T00:00:00Z"),
        Instant.parse("2026-08-03T00:15:00Z"),
        type.name().toLowerCase() + "-jti",
        type == JwtTokenType.SIGNUP ? null : "session");
  }
}
