package com.poppang.be.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtFingerprint;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.common.ratelimit.V2AuthRateLimitScope;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = SecurityChainContractTest.SecurityContractController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=true",
      "springdoc.api-docs.path=/custom-docs",
      "springdoc.swagger-ui.enabled=true",
      "springdoc.swagger-ui.path=/custom-swagger"
    })
@Import({SecurityConfig.class, SecurityChainContractTest.SecurityContractController.class})
class SecurityChainContractTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String ACCESS_TOKEN = UUID.randomUUID().toString();
  private static final String SIGNUP_TOKEN = UUID.randomUUID().toString();
  private static final String SIGNUP_JWT_ID = UUID.randomUUID().toString();
  private static final String REFRESH_TOKEN = UUID.randomUUID().toString();
  private static final String EXPIRED_TOKEN = UUID.randomUUID().toString();
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;
  @Autowired private List<SecurityFilterChain> securityFilterChains;

  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @Autowired
  @Qualifier("v2JwtFilterRegistration")
  private FilterRegistrationBean<V2JwtAuthenticationFilter> v2JwtFilterRegistration;

  @Autowired
  @Qualifier("workerApiKeyFilterRegistration")
  private FilterRegistrationBean<WorkerApiKeyAuthenticationFilter> workerApiKeyFilterRegistration;

  @Autowired
  @Qualifier("v2AuthRateLimitFilterRegistration")
  private FilterRegistrationBean<V2AuthRateLimitFilter> v2AuthRateLimitFilterRegistration;

  @DynamicPropertySource
  static void workerApiKey(DynamicPropertyRegistry registry) {
    registry.add("internal.worker.api-key", () -> WORKER_API_KEY);
  }

  @BeforeEach
  void setUpTokens() {
    given(jwtProvider.verify(ACCESS_TOKEN)).willReturn(verified(JwtTokenType.ACCESS));
    given(jwtProvider.verify(SIGNUP_TOKEN)).willReturn(verified(JwtTokenType.SIGNUP));
    given(jwtProvider.verify(REFRESH_TOKEN)).willReturn(verified(JwtTokenType.REFRESH));
    given(jwtProvider.verify(EXPIRED_TOKEN)).willThrow(new BaseException(ErrorCode.EXPIRED_TOKEN));
    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(Optional.of(completedUser(Role.MEMBER)));
  }

  @Test
  void protectedV2RequestWithoutAuthenticationReturnsApiResponse401() throws Exception {
    mockMvc
        .perform(get("/api/v2/resource"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void chainsAreOrderedInternalThenV2ThenV1ThenInfrastructure() {
    assertThat(securityFilterChains).hasSize(4);

    MockHttpServletRequest internal = request("/api/v2/internal/resource");
    MockHttpServletRequest v2 = request("/api/v2/resource");
    MockHttpServletRequest v1 = request("/api/v1/resource");
    MockHttpServletRequest infrastructure = request("/actuator/health");

    assertThat(securityFilterChains.get(0).matches(internal)).isTrue();
    assertThat(securityFilterChains.get(0).matches(v2)).isFalse();
    assertThat(securityFilterChains.get(1).matches(internal)).isTrue();
    assertThat(securityFilterChains.get(1).matches(v2)).isTrue();
    assertThat(securityFilterChains.get(1).matches(v1)).isFalse();
    assertThat(securityFilterChains.get(2).matches(v1)).isTrue();
    assertThat(securityFilterChains.get(2).matches(infrastructure)).isFalse();
    assertThat(securityFilterChains.get(3).matches(infrastructure)).isTrue();
  }

  @Test
  void authenticationFiltersAreNotRegisteredAsGlobalServletFilters() {
    assertThat(v2JwtFilterRegistration.isEnabled()).isFalse();
    assertThat(workerApiKeyFilterRegistration.isEnabled()).isFalse();
    assertThat(v2AuthRateLimitFilterRegistration.isEnabled()).isFalse();
  }

  @Test
  void exactMobileLoginRefreshAndWebReadPathsArePublicAndIgnoreBearerToken() throws Exception {
    mockMvc.perform(post("/api/v2/auth/google/mobile/login")).andExpect(status().isOk());
    mockMvc.perform(post("/api/v2/auth/refresh")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/web/resource")).andExpect(status().isOk());
    mockMvc.perform(head("/api/v2/web/resource")).andExpect(status().isOk());

    verifyNoInteractions(jwtProvider, usersRepository);
  }

  @Test
  void eleventhSocialLoginRequestFromTheSameClientIsRateLimited() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (attempts.incrementAndGet() > 10) {
                throw new BaseException(ErrorCode.RATE_LIMIT_EXCEEDED);
              }
              return null;
            })
        .when(authRateLimiter)
        .check(eq(V2AuthRateLimitScope.LOGIN), anyString());

    for (int request = 0; request < 10; request++) {
      mockMvc.perform(post("/api/v2/auth/kakao/mobile/login")).andExpect(status().isOk());
    }

    mockMvc
        .perform(post("/api/v2/auth/kakao/mobile/login"))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EXCEEDED.getCode()));
  }

  @Test
  void sixthSignupRequestForTheSameVerifiedUserIsRateLimited() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (attempts.incrementAndGet() > 5) {
                throw new BaseException(ErrorCode.RATE_LIMIT_EXCEEDED);
              }
              return null;
            })
        .when(authRateLimiter)
        .check(eq(V2AuthRateLimitScope.SIGNUP), eq(USER_UUID));

    for (int request = 0; request < 5; request++) {
      mockMvc
          .perform(
              post("/api/v2/auth/google/signup")
                  .header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
          .andExpect(status().isOk());
    }

    mockMvc
        .perform(
            post("/api/v2/auth/google/signup")
                .header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EXCEEDED.getCode()));
  }

  @Test
  void socialWebLoginCallbacksAreNotPublic() throws Exception {
    mockMvc.perform(get("/api/v2/auth/kakao/login")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v2/auth/google/login")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v2/auth/apple/login")).andExpect(status().isUnauthorized());
  }

  @Test
  void neighboringAuthAndWebWritePathsAreNotPublic() throws Exception {
    mockMvc.perform(get("/api/v2/auth/unknown")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v2/auth/logout")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v2/auth/kakao/login")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v2/auth/refresh")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v2/auth/kakao/signup")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v2/web/resource")).andExpect(status().isUnauthorized());
  }

  @Test
  void accessTokenUsesCurrentDatabaseRoleForGeneralAndAdminAuthorization() throws Exception {
    mockMvc
        .perform(get("/api/v2/resource").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(USER_UUID));

    mockMvc
        .perform(
            get("/api/v2/admin/resource").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_TOKEN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(completedUser(Role.ADMIN)));

    mockMvc
        .perform(
            get("/api/v2/admin/resource").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_TOKEN)))
        .andExpect(status().isOk());
  }

  @Test
  void signupTokenCanUseOnlyTheThreeSignupPosts() throws Exception {
    mockMvc
        .perform(
            post("/api/v2/auth/kakao/signup")
                .header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v2/auth/google/signup")
                .header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v2/auth/apple/signup")
                .header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v2/resource").header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));
    mockMvc
        .perform(
            get("/api/v2/admin/resource").header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));

    mockMvc
        .perform(
            post("/api/v2/auth/kakao/signup")
                .header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_TOKEN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));
  }

  @Test
  void signupAuthenticationCarriesOnlyRedactedFingerprintAndJwtIdDetails() throws Exception {
    mockMvc
        .perform(
            post("/api/v2/auth/kakao/signup")
                .header(HttpHeaders.AUTHORIZATION, bearer(SIGNUP_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fingerprintMatches").value(true))
        .andExpect(jsonPath("$.data.jwtIdMatches").value(true))
        .andExpect(jsonPath("$.data.rawTokenRetained").value(false))
        .andExpect(jsonPath("$.data.toStringRedacted").value(true));
  }

  @Test
  void refreshTokenCannotBeUsedAsBearerAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v2/resource").header(HttpHeaders.AUTHORIZATION, bearer(REFRESH_TOKEN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));
  }

  @Test
  void expiredTokenReturnsItsJwtErrorAsApiResponse() throws Exception {
    mockMvc
        .perform(get("/api/v2/resource").header(HttpHeaders.AUTHORIZATION, bearer(EXPIRED_TOKEN)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.EXPIRED_TOKEN.getCode()));
  }

  @Test
  void missingDeletedAndPendingUsersCannotAuthenticateWithAccessToken() throws Exception {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.empty());
    mockMvc
        .perform(get("/api/v2/resource").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_TOKEN)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));

    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(Optional.of(user(Role.MEMBER, SignupStatus.COMPLETED, true)));
    mockMvc
        .perform(get("/api/v2/resource").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_TOKEN)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_NOT_ACTIVE.getCode()));

    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(Optional.of(user(Role.MEMBER, SignupStatus.PENDING, false)));
    mockMvc
        .perform(get("/api/v2/resource").header(HttpHeaders.AUTHORIZATION, bearer(ACCESS_TOKEN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));
  }

  @Test
  void internalApiRequiresTheConfiguredWorkerApiKey() throws Exception {
    mockMvc
        .perform(get("/api/v2/internal/resource"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_WORKER_API_KEY.getCode()));
    mockMvc
        .perform(get("/api/v2/internal/resource").header("X-Worker-Api-Key", "wrong"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_WORKER_API_KEY.getCode()));
    mockMvc
        .perform(get("/api/v2/internal/resource").header("X-Worker-Api-Key", WORKER_API_KEY))
        .andExpect(status().isOk());
  }

  @Test
  void customSpringDocPathsRemainPublicAndDefaultSwaggerPathsReturnApiResponse403()
      throws Exception {
    mockMvc
        .perform(get("/custom-docs"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getStatus())
                    .isNotIn(
                        ErrorCode.AUTHENTICATION_REQUIRED.getHttpStatus().value(),
                        ErrorCode.INSUFFICIENT_AUTHORITY.getHttpStatus().value()));
    mockMvc
        .perform(get("/custom-swagger"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getStatus())
                    .isNotIn(
                        ErrorCode.AUTHENTICATION_REQUIRED.getHttpStatus().value(),
                        ErrorCode.INSUFFICIENT_AUTHORITY.getHttpStatus().value()));
    mockMvc
        .perform(get("/swagger-ui/index.html"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getStatus())
                    .isNotIn(
                        ErrorCode.AUTHENTICATION_REQUIRED.getHttpStatus().value(),
                        ErrorCode.INSUFFICIENT_AUTHORITY.getHttpStatus().value()));

    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_AUTHORITY.getCode()));
  }

  private VerifiedJwt verified(JwtTokenType tokenType) {
    String sessionId = tokenType == JwtTokenType.SIGNUP ? null : UUID.randomUUID().toString();
    return new VerifiedJwt(
        USER_UUID,
        tokenType,
        tokenType == JwtTokenType.SIGNUP ? "signup-audience" : "app-audience",
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"),
        tokenType == JwtTokenType.SIGNUP ? SIGNUP_JWT_ID : UUID.randomUUID().toString(),
        sessionId);
  }

  private Users completedUser(Role role) {
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

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    return request;
  }

  @RestController
  public static class SecurityContractController {

    @GetMapping({
      "/api/v2/auth/kakao/login",
      "/api/v2/auth/google/login",
      "/api/v2/auth/apple/login",
      "/api/v2/web/resource",
      "/api/v2/resource",
      "/api/v2/admin/resource",
      "/api/v2/internal/resource",
      "/api/v2/auth/unknown"
    })
    ApiResponse<String> read(Authentication authentication) {
      if (authentication != null
          && authentication.getPrincipal() instanceof JwtPrincipal principal) {
        return ApiResponse.ok(principal.userUuid());
      }
      return ApiResponse.ok("ok");
    }

    @PostMapping({
      "/api/v2/auth/kakao/mobile/login",
      "/api/v2/auth/google/mobile/login",
      "/api/v2/auth/apple/mobile/login",
      "/api/v2/auth/refresh",
      "/api/v2/auth/google/signup",
      "/api/v2/auth/apple/signup",
      "/api/v2/auth/logout",
      "/api/v2/web/resource"
    })
    ApiResponse<String> write() {
      return ApiResponse.ok("ok");
    }

    @PostMapping("/api/v2/auth/kakao/signup")
    ApiResponse<java.util.Map<String, Boolean>> signupDetails(Authentication authentication)
        throws Exception {
      Object details = authentication.getDetails();
      String fingerprint =
          details == null
              ? null
              : (String) details.getClass().getMethod("tokenFingerprint").invoke(details);
      String jwtId =
          details == null ? null : (String) details.getClass().getMethod("jwtId").invoke(details);
      String detailsText = String.valueOf(details);
      return ApiResponse.ok(
          java.util.Map.of(
              "fingerprintMatches", JwtFingerprint.sha256(SIGNUP_TOKEN).equals(fingerprint),
              "jwtIdMatches", SIGNUP_JWT_ID.equals(jwtId),
              "rawTokenRetained", detailsText.contains(SIGNUP_TOKEN),
              "toStringRedacted",
                  detailsText.contains("[REDACTED]")
                      && !detailsText.contains(String.valueOf(fingerprint))
                      && !detailsText.contains(String.valueOf(jwtId))));
    }
  }
}
