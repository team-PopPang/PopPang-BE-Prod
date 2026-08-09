package com.poppang.be.domain.auth.presentation.v2;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.exception.GlobalExceptionHandler;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.auth.application.V2QaTokenService;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2QaTokenController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2QaTokenControllerTest {

  private static final String QA_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2QaTokenService qaTokenService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @DynamicPropertySource
  static void authProperties(DynamicPropertyRegistry registry) {
    registry.add("internal.worker.api-key", () -> WORKER_API_KEY);
    registry.add("qa.auth.api-key", () -> QA_API_KEY);
    registry.add("qa.auth.member-user-uuid", () -> "11111111-1111-1111-1111-111111111111");
    registry.add("qa.auth.admin-user-uuid", () -> "22222222-2222-2222-2222-222222222222");
  }

  @Test
  void returnsMemberTokensWithNoStoreHeadersForTheConfiguredKey() throws Exception {
    given(qaTokenService.issueTokens("MEMBER"))
        .willReturn(
            new V2TokenResponseDto("Bearer", "access.token", "refresh.token", 900, 2_592_000));

    mockMvc
        .perform(
            post("/api/v2/test-auth/token")
                .queryParam("account", "MEMBER")
                .header("X-QA-Api-Key", QA_API_KEY))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.accessToken").value("access.token"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"));

    verify(qaTokenService).issueTokens("MEMBER");
  }

  @Test
  void usesTheSameKeyForTheAdminAccount() throws Exception {
    given(qaTokenService.issueTokens("ADMIN"))
        .willReturn(
            new V2TokenResponseDto("Bearer", "admin.access", "admin.refresh", 900, 2_592_000));

    mockMvc
        .perform(
            post("/api/v2/test-auth/token")
                .queryParam("account", "ADMIN")
                .header("X-QA-Api-Key", QA_API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("admin.access"));

    verify(qaTokenService).issueTokens("ADMIN");
  }

  @Test
  void rejectsMissingAndWrongKeysBeforeCallingTheService() throws Exception {
    mockMvc
        .perform(post("/api/v2/test-auth/token").queryParam("account", "MEMBER"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_QA_API_KEY.getCode()));
    mockMvc
        .perform(
            post("/api/v2/test-auth/token")
                .queryParam("account", "MEMBER")
                .header("X-QA-Api-Key", "wrong-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_QA_API_KEY.getCode()));
    mockMvc
        .perform(
            post("/api/v2/test-auth/token")
                .queryParam("account", "MEMBER")
                .queryParam("X-QA-Api-Key", QA_API_KEY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_QA_API_KEY.getCode()));

    verifyNoInteractions(qaTokenService);
  }

  @Test
  void rejectsInvalidOrMissingAccountValues() throws Exception {
    given(qaTokenService.issueTokens(nullable(String.class)))
        .willThrow(new BaseException(ErrorCode.INVALID_QA_ACCOUNT));

    mockMvc
        .perform(
            post("/api/v2/test-auth/token")
                .queryParam("account", "OWNER")
                .header("X-QA-Api-Key", QA_API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_QA_ACCOUNT.getCode()));
    mockMvc
        .perform(post("/api/v2/test-auth/token").header("X-QA-Api-Key", QA_API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_QA_ACCOUNT.getCode()));
  }
}
