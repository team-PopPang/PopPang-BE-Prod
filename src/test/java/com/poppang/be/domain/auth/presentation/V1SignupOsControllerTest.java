package com.poppang.be.domain.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.GlobalExceptionHandler;
import com.poppang.be.common.mail.EmailService;
import com.poppang.be.domain.auth.apple.application.AppleAuthServiceImpl;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import com.poppang.be.domain.auth.application.AuthService;
import com.poppang.be.domain.auth.google.application.GoogleAuthServiceImpl;
import com.poppang.be.domain.auth.google.config.GoogleProperties;
import com.poppang.be.domain.auth.kakao.application.KakaoAuthServiceImpl;
import com.poppang.be.domain.auth.kakao.config.KakaoProperties;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupOs;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class V1SignupOsControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private UsersRepository users;
  private UserAlertKeywordRepository keywords;
  private UserRecommendRepository userRecommends;
  private EmailService email;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    users = mock(UsersRepository.class);
    keywords = mock(UserAlertKeywordRepository.class);
    userRecommends = mock(UserRecommendRepository.class);
    RecommendRepository recommends = mock(RecommendRepository.class);
    email = mock(EmailService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new AuthController(
                    new KakaoAuthServiceImpl(
                        mock(KakaoProperties.class),
                        users,
                        keywords,
                        userRecommends,
                        recommends,
                        email),
                    new AppleAuthServiceImpl(
                        mock(AppleProperties.class),
                        users,
                        keywords,
                        userRecommends,
                        recommends,
                        email),
                    new GoogleAuthServiceImpl(
                        mock(GoogleProperties.class),
                        users,
                        keywords,
                        userRecommends,
                        recommends,
                        email),
                    mock(AuthService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @ParameterizedTest
  @CsvSource({
    "kakao,IOS",
    "kakao,ANDROID",
    "google,IOS",
    "google,ANDROID",
    "apple,IOS",
    "apple,ANDROID"
  })
  void recordsOsForAllProvidersWithoutChangingResponse(String provider, SignupOs os)
      throws Exception {
    Users user = pending(provider);
    Map<String, Object> request = request();
    request.put("signupOs", os.name());

    signup(provider, request);

    assertThat(user.getSignupOs()).isEqualTo(os);
    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
    verify(users).findByUidForUpdate("social-user");
    verify(email).sendNewUserSignUpMail(user);
  }

  @ParameterizedTest
  @ValueSource(strings = {"kakao", "google", "apple"})
  void missingOrNullOsKeepsLegacySignupWorking(String provider) throws Exception {
    Users withoutField = pending(provider);
    signup(provider, request());
    assertThat(withoutField.getSignupOs()).isNull();
    assertThat(withoutField.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);

    Users withNull = pending(provider);
    Map<String, Object> request = request();
    request.put("signupOs", null);
    signup(provider, request);
    assertThat(withNull.getSignupOs()).isNull();
    assertThat(withNull.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"kakao", "google", "apple"})
  void invalidOsReturns400BeforeMutatingUser(String provider) throws Exception {
    Users user = pending(provider);
    for (String invalid : List.of("", " ", "ios", "AOS", "WINDOWS")) {
      Map<String, Object> request = request();
      request.put("signupOs", invalid);
      mvc.perform(
              post(path(provider))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(4206));
    }
    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.PENDING);
    assertThat(user.getNickname()).isNull();
    assertThat(user.getSignupOs()).isNull();
    verify(users, never()).save(any());
    verifyNoInteractions(keywords, userRecommends, email);
  }

  @ParameterizedTest
  @ValueSource(strings = {"kakao", "google", "apple"})
  void repeatedSignupCannotOverwriteKnownOrUnknownOriginalOs(String provider) throws Exception {
    Users known = pending(provider);
    Map<String, Object> first = request();
    first.put("signupOs", "IOS");
    signup(provider, first);

    Map<String, Object> second = request();
    second.put("signupOs", "ANDROID");
    signup(provider, second);
    signup(provider, request());
    assertThat(known.getSignupOs()).isEqualTo(SignupOs.IOS);

    Users unknown = pending(provider);
    signup(provider, request());
    signup(provider, second);
    assertThat(unknown.getSignupOs()).isNull();
  }

  private Users pending(String provider) {
    Users user =
        Users.builder()
            .uid("social-user")
            .uuid("user-uuid")
            .provider(Provider.valueOf(provider.toUpperCase(java.util.Locale.ROOT)))
            .role(Role.MEMBER)
            .build();
    when(users.findByUidForUpdate("social-user")).thenReturn(Optional.of(user));
    return user;
  }

  private Map<String, Object> request() {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("uid", "social-user");
    request.put("nickname", "팝팡");
    request.put("email", "user@example.com");
    request.put("isAlerted", true);
    request.put("fcmToken", "fcm-token");
    request.put("alertKeywordList", List.of("성수"));
    request.put("recommendList", List.of());
    return request;
  }

  private void signup(String provider, Map<String, Object> request) throws Exception {
    mvc.perform(
            post(path(provider))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userUuid").value("user-uuid"))
        .andExpect(jsonPath("$.nickname").value("팝팡"))
        .andExpect(jsonPath("$.isAlerted").value(true))
        .andExpect(jsonPath("$.signupOs").doesNotExist());
  }

  private String path(String provider) {
    return "/api/v1/auth/" + provider + "/signup";
  }
}
