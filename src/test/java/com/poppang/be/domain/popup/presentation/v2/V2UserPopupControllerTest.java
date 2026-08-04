package com.poppang.be.domain.popup.presentation.v2;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.poppang.be.domain.popup.application.V2UserPopupService;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollItemResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2UserPopupController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2UserPopupControllerTest {

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

  @MockitoBean private V2UserPopupService popupService;
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
  void accessTokenCallsAllTwelveApisUsingOnlyThePrincipalUuid() throws Exception {
    V2UserPopupResponseDto popup = popupResponse();
    given(popupService.getAllPopupList(USER_UUID)).willReturn(List.of(popup));
    given(popupService.getPopupByUuid(USER_UUID, POPUP_UUID)).willReturn(popup);
    given(popupService.getUpcomingPopupList(USER_UUID, 7)).willReturn(List.of(popup));
    given(popupService.getSearchPopupList(USER_UUID, "성수")).willReturn(List.of(popup));
    given(popupService.getInProgressPopupList(USER_UUID)).willReturn(List.of(popup));
    given(popupService.getRandomPopupList(USER_UUID)).willReturn(List.of(popup));
    given(popupService.getScrollPopupList(USER_UUID, 30L))
        .willReturn(
            new V2UserPopupScrollResponseDto(
                List.of(
                    new V2UserPopupScrollItemResponseDto(
                        POPUP_UUID, "/images/thumbnail.jpg", "서울", "팝업", null, null, true)),
                20L,
                true));
    given(popupService.getFilteredHomePopupList(USER_UUID, "서울", "성동구", HomeSortStandard.NEWEST))
        .willReturn(List.of(popup));
    given(
            popupService.getFilteredMapPopupList(
                USER_UUID, "서울", "성동구", 37.5, 127.0, MapSortStandard.CLOSEST))
        .willReturn(List.of(popup));
    given(popupService.getRecommendPopupList(USER_UUID)).willReturn(List.of(popup));
    given(popupService.getRelatedPopupList(USER_UUID, POPUP_UUID)).willReturn(List.of(popup));
    given(popupService.getRecommendationPopupList(USER_UUID, 21L)).willReturn(List.of(popup));

    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups").queryParam("userUuid", OTHER_USER_UUID), ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].isFavorited").value(true))
        .andExpect(jsonPath("$[0].favorited").doesNotExist());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/{popupUuid}", POPUP_UUID)
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/upcoming")
                    .queryParam("upcomingDays", "7")
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/search")
                    .queryParam("q", "성수")
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/inProgress").queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/random").queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/scroll")
                    .queryParam("cursor", "30")
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].popupUuid").value(POPUP_UUID))
        .andExpect(jsonPath("$.items[0].thumbnailUrl").value("/images/thumbnail.jpg"))
        .andExpect(jsonPath("$.items[0].isFavorited").value(true))
        .andExpect(jsonPath("$.items[0].favorited").doesNotExist())
        .andExpect(jsonPath("$.nextCursor").value(20))
        .andExpect(jsonPath("$.hasNext").value(true));
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/filtered/home")
                    .queryParam("region", "서울")
                    .queryParam("district", "성동구")
                    .queryParam("homeSortStandard", "NEWEST")
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/filtered/map")
                    .queryParam("region", "서울")
                    .queryParam("district", "성동구")
                    .queryParam("latitude", "37.5")
                    .queryParam("longitude", "127.0")
                    .queryParam("mapSortStandard", "CLOSEST")
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/recommend").queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/{popupUuid}/related", POPUP_UUID)
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withToken(
                get("/api/v2/user/popups/recommendations/{recommendId}", 21L)
                    .queryParam("userUuid", OTHER_USER_UUID),
                ACCESS_TOKEN))
        .andExpect(status().isOk());

    verify(popupService).getAllPopupList(USER_UUID);
    verify(popupService).getPopupByUuid(USER_UUID, POPUP_UUID);
    verify(popupService).getUpcomingPopupList(USER_UUID, 7);
    verify(popupService).getSearchPopupList(USER_UUID, "성수");
    verify(popupService).getInProgressPopupList(USER_UUID);
    verify(popupService).getRandomPopupList(USER_UUID);
    verify(popupService).getScrollPopupList(USER_UUID, 30L);
    verify(popupService).getFilteredHomePopupList(USER_UUID, "서울", "성동구", HomeSortStandard.NEWEST);
    verify(popupService)
        .getFilteredMapPopupList(USER_UUID, "서울", "성동구", 37.5, 127.0, MapSortStandard.CLOSEST);
    verify(popupService).getRecommendPopupList(USER_UUID);
    verify(popupService).getRelatedPopupList(USER_UUID, POPUP_UUID);
    verify(popupService).getRecommendationPopupList(USER_UUID, 21L);
  }

  @Test
  void everyApiRequiresAnAccessToken() throws Exception {
    for (MockHttpServletRequestBuilder request : protectedRequests()) {
      mockMvc.perform(request).andExpect(status().isUnauthorized());
    }
    verifyNoInteractions(popupService);
  }

  @Test
  void invalidSignupAndRefreshTokensCannotCallTheApis() throws Exception {
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
    verifyNoInteractions(popupService);
  }

  @Test
  void searchKeepsTheLegacyMissingQueryErrorAndPassesBlankValueToTheService() throws Exception {
    given(popupService.getSearchPopupList(USER_UUID, "   ")).willReturn(List.of());

    mockMvc
        .perform(withToken(get("/api/v2/user/popups/search"), ACCESS_TOKEN))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()));
    mockMvc
        .perform(withToken(get("/api/v2/user/popups/search").queryParam("q", "   "), ACCESS_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    verify(popupService).getSearchPopupList(USER_UUID, "   ");
  }

  private MockHttpServletRequestBuilder[] protectedRequests() {
    return new MockHttpServletRequestBuilder[] {
      get("/api/v2/user/popups"),
      get("/api/v2/user/popups/{popupUuid}", POPUP_UUID),
      get("/api/v2/user/popups/upcoming"),
      get("/api/v2/user/popups/search").queryParam("q", "성수"),
      get("/api/v2/user/popups/inProgress"),
      get("/api/v2/user/popups/random"),
      get("/api/v2/user/popups/scroll"),
      get("/api/v2/user/popups/filtered/home")
          .queryParam("region", "서울")
          .queryParam("district", "성동구")
          .queryParam("homeSortStandard", "NEWEST"),
      get("/api/v2/user/popups/filtered/map")
          .queryParam("region", "서울")
          .queryParam("district", "성동구")
          .queryParam("mapSortStandard", "NEWEST"),
      get("/api/v2/user/popups/recommend"),
      get("/api/v2/user/popups/{popupUuid}/related", POPUP_UUID),
      get("/api/v2/user/popups/recommendations/{recommendId}", 21L)
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
        Instant.parse("2026-08-03T00:00:00Z"),
        Instant.parse("2026-08-03T00:15:00Z"),
        type.name().toLowerCase() + "-jti",
        type == JwtTokenType.SIGNUP ? null : "session");
  }

  private V2UserPopupResponseDto popupResponse() {
    return new V2UserPopupResponseDto(
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
