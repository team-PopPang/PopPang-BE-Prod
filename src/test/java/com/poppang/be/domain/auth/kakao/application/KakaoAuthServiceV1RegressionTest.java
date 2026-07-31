package com.poppang.be.domain.auth.kakao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.poppang.be.common.mail.EmailService;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
import com.poppang.be.domain.auth.kakao.config.KakaoProperties;
import com.poppang.be.domain.auth.kakao.dto.request.KakaoAppLoginRequestDto;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class KakaoAuthServiceV1RegressionTest {

  private static final String UID = "12345";
  private static final String TOKEN_URI = "https://mock.kakao.test/oauth/token";
  private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

  @Mock private UsersRepository usersRepository;
  @Mock private UserAlertKeywordRepository keywordRepository;
  @Mock private UserRecommendRepository userRecommendRepository;
  @Mock private RecommendRepository recommendRepository;
  @Mock private EmailService emailService;

  private KakaoAuthServiceImpl service;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    KakaoProperties properties = new KakaoProperties();
    properties.setClientId("test-client");
    properties.setRedirectUri("https://example.test/callback");
    properties.setTokenUri(TOKEN_URI);
    properties.setUserInfoUri(USER_INFO_URI);
    service =
        new KakaoAuthServiceImpl(
            properties,
            usersRepository,
            keywordRepository,
            userRecommendRepository,
            recommendRepository,
            emailService);
    RestTemplate restTemplate =
        (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
    server = MockRestServiceServer.bindTo(restTemplate).build();
  }

  @Test
  void mobileLoginKeepsTheLegacyResponseAndUidLookup() {
    KakaoAppLoginRequestDto request = new KakaoAppLoginRequestDto();
    ReflectionTestUtils.setField(request, "accessToken", "provider-token");
    Users existing =
        Users.builder()
            .uid(UID)
            .uuid("user-uuid")
            .provider(Provider.KAKAO)
            .role(Role.MEMBER)
            .signupStatus(SignupStatus.PENDING)
            .build();
    server
        .expect(once(), requestTo(USER_INFO_URI))
        .andExpect(method(GET))
        .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));
    when(usersRepository.findByUid(UID)).thenReturn(Optional.of(existing));

    LoginResponseDto response = service.mobileLogin(request);

    assertThat(response.getUid()).isEqualTo(UID);
    assertThat(response.getUserUuid()).isEqualTo("user-uuid");
    assertThat(response.getProvider()).isEqualTo(Provider.KAKAO);
    assertThat(response.getEmail()).isNull();
    verify(usersRepository).findByUid(UID);
    server.verify();
  }

  @Test
  void newLegacyKakaoWebUserStillStartsPending() {
    server
        .expect(once(), requestTo(TOKEN_URI))
        .andExpect(method(POST))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"provider-token\",\"token_type\":\"bearer\"}",
                MediaType.APPLICATION_JSON));
    server
        .expect(once(), requestTo(USER_INFO_URI))
        .andExpect(method(GET))
        .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));
    when(usersRepository.findByUid(UID)).thenReturn(Optional.empty());
    when(usersRepository.save(any(Users.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    LoginResponseDto response = service.webLogin("authorization-code");

    assertThat(response.getProvider()).isEqualTo(Provider.KAKAO);
    assertThat(response.getRole()).isEqualTo(Role.MEMBER);
    assertThat(response.getEmail()).isNull();
    verify(usersRepository).save(any(Users.class));
    server.verify();
  }

  @Test
  void legacySignupKeepsItsBodyAndUidLookup() {
    SignupRequestDto request = new SignupRequestDto();
    ReflectionTestUtils.setField(request, "uid", UID);
    ReflectionTestUtils.setField(request, "provider", Provider.GOOGLE);
    ReflectionTestUtils.setField(request, "role", Role.ADMIN);
    ReflectionTestUtils.setField(request, "email", "legacy@example.com");
    ReflectionTestUtils.setField(request, "nickname", "legacy-nickname");
    ReflectionTestUtils.setField(request, "alerted", true);
    ReflectionTestUtils.setField(request, "fcmToken", "legacy-fcm");
    ReflectionTestUtils.setField(request, "alertKeywordList", List.of());
    ReflectionTestUtils.setField(request, "recommendList", List.of());
    Users pending =
        Users.builder()
            .uid(UID)
            .uuid("user-uuid")
            .provider(Provider.KAKAO)
            .role(Role.MEMBER)
            .signupStatus(SignupStatus.PENDING)
            .build();
    when(usersRepository.findByUid(UID)).thenReturn(Optional.of(pending));

    SignupResponseDto response = service.signup(request);

    assertThat(response.getProvider()).isEqualTo(Provider.KAKAO);
    assertThat(response.getRole()).isEqualTo(Role.MEMBER);
    assertThat(pending.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
    verify(usersRepository).findByUid(UID);
  }
}
