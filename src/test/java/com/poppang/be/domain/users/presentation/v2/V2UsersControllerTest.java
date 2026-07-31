package com.poppang.be.domain.users.presentation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.GlobalExceptionHandler;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.users.application.V2UsersService;
import com.poppang.be.domain.users.dto.v2.request.V2ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateFcmTokenRequestDto;
import com.poppang.be.domain.users.dto.v2.response.V2NicknameDuplicateResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UpdateAlertStatusResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UserResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2UsersController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class V2UsersControllerTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER_USER_UUID = "99999999-9999-9999-9999-999999999999";
  private static final String ACCESS_TOKEN = "access.token";
  private static final String SIGNUP_TOKEN = "signup.token";
  private static final String WORKER_API_KEY =
      UUID.randomUUID().toString() + UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;
  @Autowired private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private V2UsersService usersService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @DynamicPropertySource
  static void workerApiKey(DynamicPropertyRegistry registry) {
    registry.add("internal.worker.api-key", () -> WORKER_API_KEY);
  }

  @BeforeEach
  void setUpAuthentication() {
    given(jwtProvider.verify(ACCESS_TOKEN)).willReturn(accessJwt());
    given(jwtProvider.verify(SIGNUP_TOKEN)).willReturn(signupJwt());
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(completedUser(false)));
  }

  @Test
  void accessTokenCallsAllSixApisWithOnlyThePrincipalUserUuid() throws Exception {
    given(usersService.getUser(USER_UUID))
        .willReturn(
            new V2UserResponseDto(
                USER_UUID, Provider.KAKAO, "user@example.com", "팝팡", Role.MEMBER, true));
    given(usersService.updateAlertStatus(USER_UUID, new V2UpdateAlertStatusRequestDto(false)))
        .willReturn(new V2UpdateAlertStatusResponseDto(USER_UUID, false));
    given(usersService.checkNicknameDuplicated(USER_UUID, "팝팡"))
        .willReturn(new V2NicknameDuplicateResponseDto(true));

    mockMvc
        .perform(withAccess(get("/api/v2/user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userUuid").value(USER_UUID))
        .andExpect(jsonPath("$.data.uid").doesNotExist())
        .andExpect(jsonPath("$.data.fcmToken").doesNotExist())
        .andExpect(jsonPath("$.data.accessToken").doesNotExist())
        .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    mockMvc
        .perform(
            withAccess(
                patch("/api/v2/user/alert-status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"isAlerted\":false}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isAlerted").value(false));
    mockMvc
        .perform(withAccess(get("/api/v2/user/nickname/duplicated").queryParam("nickname", "팝팡")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isDuplicated").value(true));
    mockMvc
        .perform(
            withAccess(
                patch("/api/v2/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"새 닉네임\"}")))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
    mockMvc
        .perform(withAccess(delete("/api/v2/user")))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
    mockMvc
        .perform(
            withAccess(
                put("/api/v2/user/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fcmToken\":\"sensitive-fcm-token\"}")))
        .andExpect(status().isOk())
        .andExpect(content().string(""));

    verify(usersService).getUser(USER_UUID);
    verify(usersService).updateAlertStatus(USER_UUID, new V2UpdateAlertStatusRequestDto(false));
    verify(usersService).checkNicknameDuplicated(USER_UUID, "팝팡");
    ArgumentCaptor<V2ChangeNicknameRequestDto> nicknameRequest =
        ArgumentCaptor.forClass(V2ChangeNicknameRequestDto.class);
    verify(usersService)
        .changeNickname(org.mockito.ArgumentMatchers.eq(USER_UUID), nicknameRequest.capture());
    assertThat(nicknameRequest.getValue().getNickname()).isEqualTo("새 닉네임");
    verify(usersService).softDelete(USER_UUID);
    verify(usersService)
        .updateFcmToken(USER_UUID, new V2UpdateFcmTokenRequestDto("sensitive-fcm-token"));
  }

  @Test
  void allSixApisRequireAnAccessToken() throws Exception {
    for (MockHttpServletRequestBuilder request : protectedRequests()) {
      mockMvc.perform(request).andExpect(status().isUnauthorized());
    }

    verifyNoInteractions(usersService);
  }

  @Test
  void signupTokenCannotCallAnyOfTheSixApis() throws Exception {
    for (MockHttpServletRequestBuilder request : protectedRequests()) {
      mockMvc
          .perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + SIGNUP_TOKEN))
          .andExpect(status().isForbidden());
    }

    verifyNoInteractions(usersService);
  }

  @Test
  void callerSuppliedUuidCannotChangeTheTargetUser() throws Exception {
    mockMvc
        .perform(
            withAccess(
                patch("/api/v2/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userUuid\":\"" + OTHER_USER_UUID + "\",\"nickname\":\"새 닉네임\"}")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            withAccess(
                get("/api/v2/user/nickname/duplicated")
                    .queryParam("nickname", "팝팡")
                    .queryParam("userUuid", OTHER_USER_UUID)))
        .andExpect(status().isOk());

    ArgumentCaptor<V2ChangeNicknameRequestDto> nicknameRequest =
        ArgumentCaptor.forClass(V2ChangeNicknameRequestDto.class);
    verify(usersService)
        .changeNickname(org.mockito.ArgumentMatchers.eq(USER_UUID), nicknameRequest.capture());
    assertThat(nicknameRequest.getValue().getNickname()).isEqualTo("새 닉네임");
    verify(usersService).checkNicknameDuplicated(USER_UUID, "팝팡");
  }

  @Test
  void deletedUserIsRejectedBeforeTheControllerOnTheNextRequest() throws Exception {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(completedUser(true)));

    mockMvc
        .perform(withAccess(get("/api/v2/user")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(5010));

    verifyNoInteractions(usersService);
  }

  @Test
  void controllerExposesOnlyTheSixApprovedMappings() {
    Set<String> mappings = new HashSet<>();
    handlerMapping
        .getHandlerMethods()
        .forEach(
            (mapping, method) -> {
              if (!method.getBeanType().equals(V2UsersController.class)) {
                return;
              }
              for (String path : mapping.getPatternValues()) {
                for (RequestMethod requestMethod : mapping.getMethodsCondition().getMethods()) {
                  mappings.add(requestMethod.name() + " " + path);
                }
              }
            });

    assertThat(mappings)
        .containsExactlyInAnyOrder(
            "GET /api/v2/user",
            "PATCH /api/v2/user/alert-status",
            "GET /api/v2/user/nickname/duplicated",
            "PATCH /api/v2/user",
            "DELETE /api/v2/user",
            "PUT /api/v2/user/fcm-token")
        .noneMatch(
            mapping ->
                mapping.contains("hard-delete")
                    || mapping.contains("restore")
                    || mapping.contains("resotre")
                    || mapping.contains("duplicate-check")
                    || mapping.matches(".*\\{.*[Uu]uid.*}.*"));
  }

  private MockHttpServletRequestBuilder withAccess(MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN);
  }

  private MockHttpServletRequestBuilder[] protectedRequests() {
    return new MockHttpServletRequestBuilder[] {
      get("/api/v2/user"),
      patch("/api/v2/user/alert-status")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"isAlerted\":true}"),
      get("/api/v2/user/nickname/duplicated").queryParam("nickname", "팝팡"),
      patch("/api/v2/user")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"nickname\":\"팝팡\"}"),
      delete("/api/v2/user"),
      put("/api/v2/user/fcm-token")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"fcmToken\":\"fcm-token\"}")
    };
  }

  private VerifiedJwt accessJwt() {
    return new VerifiedJwt(
        USER_UUID,
        JwtTokenType.ACCESS,
        "poppang-app-v2",
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"),
        "access-jti",
        "access-session");
  }

  private VerifiedJwt signupJwt() {
    return new VerifiedJwt(
        USER_UUID,
        JwtTokenType.SIGNUP,
        "poppang-signup-v2",
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"),
        "signup-jti",
        null);
  }

  private Users completedUser(boolean deleted) {
    return Users.builder()
        .uuid(USER_UUID)
        .role(Role.MEMBER)
        .signupStatus(SignupStatus.COMPLETED)
        .deleted(deleted)
        .build();
  }
}
