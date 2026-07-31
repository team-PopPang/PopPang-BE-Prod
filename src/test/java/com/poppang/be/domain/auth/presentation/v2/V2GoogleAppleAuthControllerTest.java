package com.poppang.be.domain.auth.presentation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.poppang.be.domain.auth.apple.application.V2AppleAuthService;
import com.poppang.be.domain.auth.dto.v2.request.V2AppleMobileLoginRequestDto;
import com.poppang.be.domain.auth.dto.v2.request.V2GoogleMobileLoginRequestDto;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2SocialAuthResponseDto;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.auth.google.application.V2GoogleAuthService;
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
import java.util.stream.Collectors;
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
    controllers = {V2GoogleAuthController.class, V2AppleAuthController.class},
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2GoogleAppleAuthControllerTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String ACCESS_TOKEN = "access.jwt";
  private static final String SIGNUP_TOKEN = "signup.jwt";
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;
  @MockitoBean private V2GoogleAuthService googleAuthService;
  @MockitoBean private V2AppleAuthService appleAuthService;
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
                    .role(Role.MEMBER)
                    .signupStatus(SignupStatus.COMPLETED)
                    .build()));
  }

  @Test
  void googleAndAppleMobileLoginArePublicAndReturnNoStoreResponses() throws Exception {
    given(googleAuthService.mobileLogin("google-id-token"))
        .willReturn(V2SocialAuthResponseDto.pending("google-signup", 900));
    given(appleAuthService.mobileLogin("apple-auth-code", "apple-raw-nonce"))
        .willReturn(V2SocialAuthResponseDto.pending("apple-signup", 900));

    mockMvc
        .perform(
            post("/api/v2/auth/google/mobile/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"google-id-token\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(jsonPath("$.data.signupToken").value("google-signup"));
    mockMvc
        .perform(
            post("/api/v2/auth/apple/mobile/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"auth_code\":\"apple-auth-code\","
                        + "\"raw_nonce\":\"apple-raw-nonce\","
                        + "\"email\":\"ignored@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(jsonPath("$.data.signupToken").value("apple-signup"));

    verify(googleAuthService).mobileLogin("google-id-token");
    verify(appleAuthService).mobileLogin("apple-auth-code", "apple-raw-nonce");
  }

  @Test
  void signupRequiresSignupTokenAndRejectsAccessToken() throws Exception {
    String body = signupBody();

    mockMvc
        .perform(
            post("/api/v2/auth/google/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
    mockMvc
        .perform(
            post("/api/v2/auth/apple/signup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));
  }

  @Test
  void providerSignupUsesOnlyJwtPrincipalAndSafeDetails() throws Exception {
    given(googleAuthService.signup(eq(USER_UUID), any(), any(JwtAuthenticationDetails.class)))
        .willReturn(completed(Provider.GOOGLE));
    given(appleAuthService.signup(eq(USER_UUID), any(), any(JwtAuthenticationDetails.class)))
        .willReturn(completed(Provider.APPLE));

    mockMvc
        .perform(
            post("/api/v2/auth/google/signup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SIGNUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.userUuid").value(USER_UUID))
        .andExpect(jsonPath("$.data.user.provider").value("GOOGLE"))
        .andExpect(jsonPath("$.data.user.uid").doesNotExist())
        .andExpect(jsonPath("$.data.user.fcmToken").doesNotExist());
    mockMvc
        .perform(
            post("/api/v2/auth/apple/signup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SIGNUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.provider").value("APPLE"));

    verify(googleAuthService).signup(eq(USER_UUID), any(), any(JwtAuthenticationDetails.class));
    verify(appleAuthService).signup(eq(USER_UUID), any(), any(JwtAuthenticationDetails.class));
  }

  @Test
  void v2WebCallbacksAreNotImplementedOrPublic() throws Exception {
    assertThat(
            Arrays.stream(V2GoogleAuthController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(GetMapping.class)))
        .isFalse();
    assertThat(
            Arrays.stream(V2AppleAuthController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(GetMapping.class)))
        .isFalse();

    mockMvc.perform(get("/api/v2/auth/google/login")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v2/auth/apple/login")).andExpect(status().isUnauthorized());
  }

  @Test
  void v2LoginAndSignupBodiesExposeNoClientControlledIdentity() {
    assertThat(componentNames(V2GoogleMobileLoginRequestDto.class))
        .containsExactly("idToken")
        .doesNotContain("uid", "userUuid", "provider", "role", "email");
    assertThat(componentNames(V2AppleMobileLoginRequestDto.class))
        .containsExactlyInAnyOrder("authorizationCode", "rawNonce")
        .doesNotContain("uid", "userUuid", "provider", "role", "email");
    assertThat(componentNames(V2SignupRequestDto.class))
        .containsExactlyInAnyOrder(
            "nickname", "alerted", "fcmToken", "alertKeywordList", "recommendList")
        .doesNotContain("uid", "userUuid", "provider", "role", "email");
  }

  private Set<String> componentNames(Class<?> recordType) {
    return Arrays.stream(recordType.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName)
        .collect(Collectors.toSet());
  }

  private String signupBody() {
    return """
        {"nickname":"nickname","isAlerted":true,"fcmToken":"fcm",
         "alertKeywordList":[],"recommendList":[]}
        """;
  }

  private V2SocialAuthResponseDto completed(Provider provider) {
    return V2SocialAuthResponseDto.completed(
        new V2AuthUserResponseDto(
            USER_UUID, provider, "mail@example.com", "nickname", Role.MEMBER, true),
        new V2TokenResponseDto("Bearer", "access", "refresh", 900, 2_592_000));
  }

  private VerifiedJwt jwt(JwtTokenType type, String sessionId) {
    return new VerifiedJwt(
        USER_UUID,
        type,
        type == JwtTokenType.SIGNUP ? "poppang-signup-v2" : "poppang-app-v2",
        Instant.parse("2026-07-31T00:00:00Z"),
        Instant.parse("2026-07-31T00:15:00Z"),
        "jwt-id",
        sessionId);
  }
}
