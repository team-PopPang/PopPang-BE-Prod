package com.poppang.be.domain.auth.presentation;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.domain.auth.apple.application.AppleAuthService;
import com.poppang.be.domain.auth.application.AuthService;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.google.application.GoogleAuthService;
import com.poppang.be.domain.auth.kakao.application.KakaoAuthService;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GoogleAppleAuthControllerV1RegressionTest {

  private GoogleAuthService googleAuthService;
  private AppleAuthService appleAuthService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    googleAuthService = mock(GoogleAuthService.class);
    appleAuthService = mock(AppleAuthService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AuthController(
                    mock(KakaoAuthService.class),
                    appleAuthService,
                    googleAuthService,
                    mock(AuthService.class)))
            .build();
  }

  @Test
  void googleV1WebAndMobileContractsRemainUnchanged() throws Exception {
    given(googleAuthService.webLogin("web-code")).willReturn(response(Provider.GOOGLE));
    given(
            googleAuthService.mobileLogin(
                argThat(request -> "google-id-token".equals(request.getIdToken()))))
        .willReturn(response(Provider.GOOGLE));

    mockMvc
        .perform(get("/api/v1/auth/google/login").param("code", "web-code"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("GOOGLE"));
    mockMvc
        .perform(
            post("/api/v1/auth/google/mobile/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"google-id-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userUuid").value("user-uuid"));
  }

  @Test
  void appleV1WebAndMobileContractsRemainUnchanged() throws Exception {
    given(appleAuthService.webLogin("web-code")).willReturn(response(Provider.APPLE));
    given(
            appleAuthService.mobileLogin(
                argThat(
                    request ->
                        "apple-auth-code".equals(request.getAuthCode())
                            && "legacy@example.com".equals(request.getEmail()))))
        .willReturn(response(Provider.APPLE));

    mockMvc
        .perform(get("/api/v1/auth/apple/login").param("code", "web-code"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("APPLE"));
    mockMvc
        .perform(
            post("/api/v1/auth/apple/mobile/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"auth_code":"apple-auth-code","email":"legacy@example.com"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userUuid").value("user-uuid"));
  }

  private LoginResponseDto response(Provider provider) {
    return LoginResponseDto.builder()
        .uid("provider-user")
        .userUuid("user-uuid")
        .provider(provider)
        .email("mail@example.com")
        .role(Role.MEMBER)
        .build();
  }
}
