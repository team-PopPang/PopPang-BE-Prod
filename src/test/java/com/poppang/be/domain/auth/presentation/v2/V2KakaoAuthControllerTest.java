package com.poppang.be.domain.auth.presentation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.exception.GlobalExceptionHandler;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2KakaoAuthResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.auth.kakao.application.V2KakaoAuthService;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.web.bind.annotation.GetMapping;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2KakaoAuthController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2KakaoAuthControllerTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String ACCESS_TOKEN = "access.jwt";
  private static final String SIGNUP_TOKEN = "signup.jwt";
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;
  @MockitoBean private V2KakaoAuthService authService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @DynamicPropertySource
  static void workerApiKey(DynamicPropertyRegistry registry) {
    registry.add("internal.worker.api-key", () -> WORKER_API_KEY);
  }

  @BeforeEach
  void setUpTokens() {
    given(jwtProvider.verify(SIGNUP_TOKEN)).willReturn(jwt(JwtTokenType.SIGNUP, null));
    given(jwtProvider.verify(ACCESS_TOKEN)).willReturn(jwt(JwtTokenType.ACCESS, "session-id"));
    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(
            Optional.of(
                Users.builder()
                    .uuid(USER_UUID)
                    .provider(Provider.KAKAO)
                    .role(Role.MEMBER)
                    .signupStatus(SignupStatus.COMPLETED)
                    .build()));
  }

  @Test
  void mobileLoginIsPublicAndReturnsPendingTokenWithNoStoreHeaders() throws Exception {
    given(authService.mobileLogin("provider-token"))
        .willReturn(V2KakaoAuthResponseDto.pending("signup", 1020));

    mockMvc
        .perform(
            post("/api/v2/auth/kakao/mobile/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"access_token\":\"provider-token\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(jsonPath("$.data.signupStatus").value("PENDING"))
        .andExpect(jsonPath("$.data.signupToken").value("signup"))
        .andExpect(jsonPath("$.data.signupTokenExpiresIn").value(1020))
        .andExpect(jsonPath("$.data.accessToken").doesNotExist())
        .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
  }

  @Test
  void signupRequiresSignupTokenAndRejectsAccessToken() throws Exception {
    String body =
        """
        {"nickname":"nickname","isAlerted":true,"fcmToken":"fcm",
         "alertKeywordList":[],"recommendList":[]}
        """;

    mockMvc
        .perform(
            post("/api/v2/auth/kakao/signup").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
    mockMvc
        .perform(
            post("/api/v2/auth/kakao/signup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));
  }

  @Test
  void signupUsesPrincipalAndSafeAuthenticationDetailsThenOmitsUidAndFcm() throws Exception {
    V2AuthUserResponseDto user =
        new V2AuthUserResponseDto(
            USER_UUID, Provider.KAKAO, "mail@example.com", "nickname", Role.MEMBER, true);
    given(authService.signup(eq(USER_UUID), any(), any(JwtAuthenticationDetails.class)))
        .willReturn(
            V2KakaoAuthResponseDto.completed(
                user, new V2TokenResponseDto("Bearer", "access", "refresh", 900, 2_592_000)));

    mockMvc
        .perform(
            post("/api/v2/auth/kakao/signup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SIGNUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nickname":"nickname","isAlerted":true,"fcmToken":"must-not-return",
                     "alertKeywordList":[],"recommendList":[]}
                    """))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(jsonPath("$.data.signupStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.user.userUuid").value(USER_UUID))
        .andExpect(jsonPath("$.data.user.uid").doesNotExist())
        .andExpect(jsonPath("$.data.user.fcmToken").doesNotExist())
        .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(900))
        .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(2_592_000))
        .andExpect(jsonPath("$.data.signupToken").doesNotExist());

    verify(authService).signup(eq(USER_UUID), any(), any(JwtAuthenticationDetails.class));
  }

  @Test
  void undecidedWebCallbackIsNotImplemented() {
    assertThat(
            Arrays.stream(V2KakaoAuthController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(GetMapping.class)))
        .isFalse();
  }

  @Test
  void signupBodySchemaContainsNoClientControlledIdentityFields() {
    Set<String> components =
        Arrays.stream(
                com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto.class
                    .getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());

    assertThat(components)
        .containsExactlyInAnyOrder(
            "nickname", "alerted", "fcmToken", "alertKeywordList", "recommendList")
        .doesNotContain("uid", "userUuid", "provider", "role", "email");
  }

  private VerifiedJwt jwt(JwtTokenType type, String sessionId) {
    return new VerifiedJwt(
        USER_UUID,
        type,
        type == JwtTokenType.SIGNUP ? "poppang-signup-v2" : "poppang-app-v2",
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"),
        "jwt-id",
        sessionId);
  }
}
