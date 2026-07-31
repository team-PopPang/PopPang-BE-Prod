package com.poppang.be.domain.auth.presentation.v2;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.poppang.be.domain.auth.application.V2TokenService;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.Instant;
import java.util.Optional;
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

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2TokenController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2TokenControllerTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String SESSION_ID = "22222222-2222-2222-2222-222222222222";
  private static final String ACCESS_TOKEN = "access.token";
  private static final String REFRESH_TOKEN = "refresh.token";
  private static final String NEW_ACCESS = "new.access.token";
  private static final String NEW_REFRESH = "new.refresh.token";
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2TokenService tokenService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @DynamicPropertySource
  static void workerApiKey(DynamicPropertyRegistry registry) {
    registry.add("internal.worker.api-key", () -> WORKER_API_KEY);
  }

  @BeforeEach
  void setUpAccessAuthentication() {
    given(jwtProvider.verify(ACCESS_TOKEN)).willReturn(accessJwt());
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(completedUser()));
  }

  @Test
  void refreshReturnsRotatedPairWithNoStoreHeaders() throws Exception {
    when(tokenService.refresh(REFRESH_TOKEN))
        .thenReturn(new V2TokenResponseDto("Bearer", NEW_ACCESS, NEW_REFRESH, 900, 2_592_000));

    mockMvc
        .perform(
            post("/api/v2/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.accessToken").value(NEW_ACCESS))
        .andExpect(jsonPath("$.data.refreshToken").value(NEW_REFRESH))
        .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(900))
        .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(2_592_000));
  }

  @Test
  void refreshRejectsMissingNullAndBlankTokenRequests() throws Exception {
    when(tokenService.refresh(isNull()))
        .thenThrow(new BaseException(ErrorCode.INVALID_REFRESH_REQUEST));
    when(tokenService.refresh("  "))
        .thenThrow(new BaseException(ErrorCode.INVALID_REFRESH_REQUEST));

    mockMvc
        .perform(post("/api/v2/auth/refresh").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v2/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REFRESH_REQUEST.getCode()));
    mockMvc
        .perform(
            post("/api/v2/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REFRESH_REQUEST.getCode()));
  }

  @Test
  void logoutUsesAuthenticatedPrincipalAndReturnsAnEmptySuccessResponse() throws Exception {
    mockMvc
        .perform(
            post("/api/v2/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().string(""));

    verify(tokenService).logout(USER_UUID, SESSION_ID);
  }

  private VerifiedJwt accessJwt() {
    return new VerifiedJwt(
        USER_UUID,
        JwtTokenType.ACCESS,
        "poppang-app-v2",
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"),
        "33333333-3333-3333-3333-333333333333",
        SESSION_ID);
  }

  private Users completedUser() {
    return Users.builder()
        .uuid(USER_UUID)
        .role(Role.MEMBER)
        .signupStatus(SignupStatus.COMPLETED)
        .deleted(false)
        .build();
  }
}
