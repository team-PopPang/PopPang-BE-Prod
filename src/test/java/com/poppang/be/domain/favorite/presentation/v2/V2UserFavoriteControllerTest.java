package com.poppang.be.domain.favorite.presentation.v2;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.poppang.be.domain.favorite.application.V2UserFavoriteService;
import com.poppang.be.domain.favorite.dto.v2.V2FavoriteCountResponseDto;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2UserFavoriteController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2UserFavoriteControllerTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER_USER_UUID = "99999999-9999-9999-9999-999999999999";
  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";
  private static final String ACCESS_TOKEN = "access.token";
  private static final String SIGNUP_TOKEN = "signup.token";
  private static final String REFRESH_TOKEN = "refresh.token";
  private static final String INVALID_TOKEN = "invalid.token";
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2UserFavoriteService favoriteService;
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
  void accessTokenCallsAllFavoriteApisWithOnlyThePrincipalUserUuid() throws Exception {
    given(favoriteService.getFavoriteCount(POPUP_UUID))
        .willReturn(new V2FavoriteCountResponseDto(10));
    given(favoriteService.getFavoritePopupList(USER_UUID)).willReturn(List.of(popupResponse()));

    mockMvc
        .perform(
            withToken(
                post("/api/v2/favorite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"userUuid":"%s","popupUuid":"%s"}
                        """
                            .formatted(OTHER_USER_UUID, POPUP_UUID)),
                ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
    mockMvc
        .perform(
            withToken(
                delete("/api/v2/favorite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"userUuid":"%s","popupUuid":"%s"}
                        """
                            .formatted(OTHER_USER_UUID, POPUP_UUID)),
                ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
    mockMvc
        .perform(
            withToken(
                get("/api/v2/favorite/count/{popupUuid}", POPUP_UUID)
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(10));
    mockMvc
        .perform(
            withToken(
                get("/api/v2/favorite/popup").queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].popupUuid").value(POPUP_UUID))
        .andExpect(jsonPath("$.data[0].isFavorited").value(true));

    verify(favoriteService).registerFavorite(USER_UUID, POPUP_UUID);
    verify(favoriteService).deleteFavorite(USER_UUID, POPUP_UUID);
    verify(favoriteService).getFavoriteCount(POPUP_UUID);
    verify(favoriteService).getFavoritePopupList(USER_UUID);
  }

  @Test
  void allFavoriteApisRequireAnAccessToken() throws Exception {
    for (MockHttpServletRequestBuilder request : protectedRequests()) {
      mockMvc.perform(request).andExpect(status().isUnauthorized());
    }
    verifyNoInteractions(favoriteService);
  }

  @Test
  void invalidSignupAndRefreshTokensCannotCallFavoriteApis() throws Exception {
    for (MockHttpServletRequestBuilder request : protectedRequests()) {
      mockMvc
          .perform(withToken(request, INVALID_TOKEN))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.getCode()));
    }
    for (MockHttpServletRequestBuilder request : protectedRequests()) {
      mockMvc.perform(withToken(request, SIGNUP_TOKEN)).andExpect(status().isForbidden());
    }
    for (MockHttpServletRequestBuilder request : protectedRequests()) {
      mockMvc.perform(withToken(request, REFRESH_TOKEN)).andExpect(status().isForbidden());
    }
    verifyNoInteractions(favoriteService);
  }

  private MockHttpServletRequestBuilder[] protectedRequests() {
    return new MockHttpServletRequestBuilder[] {
      post("/api/v2/favorite")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"popupUuid\":\"" + POPUP_UUID + "\"}"),
      delete("/api/v2/favorite")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"popupUuid\":\"" + POPUP_UUID + "\"}"),
      get("/api/v2/favorite/count/{popupUuid}", POPUP_UUID),
      get("/api/v2/favorite/popup")
    };
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
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"),
        type.name().toLowerCase() + "-jti",
        type == JwtTokenType.SIGNUP ? null : "session");
  }

  private V2FavoritePopupResponseDto popupResponse() {
    return new V2FavoritePopupResponseDto(
        POPUP_UUID,
        "팝업",
        null,
        null,
        null,
        null,
        "주소",
        null,
        "서울",
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        null,
        10,
        20,
        true);
  }
}
