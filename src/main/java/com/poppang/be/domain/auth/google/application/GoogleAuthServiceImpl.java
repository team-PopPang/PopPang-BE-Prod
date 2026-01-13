package com.poppang.be.domain.auth.google.application;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.poppang.be.common.mail.EmailService;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
import com.poppang.be.domain.auth.google.config.GoogleProperties;
import com.poppang.be.domain.auth.google.dto.request.GoogleAppLoginRequestDto;
import com.poppang.be.domain.auth.google.dto.response.GoogleTokenResponseDto;
import com.poppang.be.domain.auth.google.dto.response.GoogleUserInfoResponseDto;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;
import com.poppang.be.domain.keyword.entity.UserAlertKeyword;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

  private final GoogleProperties googleProperties;
  private final RestTemplate restTemplate = new RestTemplate();
  private final UsersRepository usersRepository;
  private final UserAlertKeywordRepository userAlertKeywordRepository;
  private final UserRecommendRepository userRecommendRepository;
  private final RecommendRepository recommendRepository;
  private final EmailService emailService;

  // Web 로그인
  @Override
  @Transactional
  public LoginResponseDto webLogin(String authCode) {
    GoogleTokenResponseDto googleToken = getAccessToken(authCode);
    if (googleToken == null
        || googleToken.getAccessToken() == null
        || googleToken.getAccessToken().isBlank()) {
      throw new IllegalStateException("Failed to retrieve google access token");
    }

    GoogleUserInfoResponseDto googleUserInfoResponseDto = getUserInfo(googleToken.getAccessToken());
    String uid = googleUserInfoResponseDto.getSub(); // sub == uid

    Users user = upsertByUid(uid, googleUserInfoResponseDto.getEmail());

    return LoginResponseDto.from(user);
  }

  // App 로그인
  @Override
  @Transactional
  public LoginResponseDto mobileLogin(GoogleAppLoginRequestDto googleAppLoginRequestDto) {
    GoogleUserInfoResponseDto googleUserInfoResponseDto =
        parseIdTokenToProfile(googleAppLoginRequestDto.getIdToken());

    String uid = googleUserInfoResponseDto.getSub();
    String email = googleUserInfoResponseDto.getEmail();

    Users user = upsertByUid(uid, email);

    return LoginResponseDto.from(user);
  }

  private GoogleUserInfoResponseDto parseIdTokenToProfile(String idToken) {
    try {
      GoogleIdTokenVerifier verifier =
          new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new JacksonFactory())
              .setAudience(
                  List.of(googleProperties.getClientId(), googleProperties.getIosClientId()))
              .build();

      GoogleIdToken token = verifier.verify(idToken);
      if (token == null) {
        throw new IllegalStateException("Invalid Google id_token");
      }

      GoogleIdToken.Payload p = token.getPayload();

      String sub = p.getSubject(); // UID
      String email = p.getEmail(); // email (동의 안 했으면 null)
      String name = (String) p.get("name");
      String picture = (String) p.get("picture");

      return new GoogleUserInfoResponseDto(sub, email, name, picture);
    } catch (Exception e) {
      throw new IllegalStateException("Google id_token verification failed", e);
    }
  }

  // 회원가입
  @Override
  @Transactional
  public SignupResponseDto signup(SignupRequestDto signupRequestDto) {

    if (usersRepository.existsByNickname(signupRequestDto.getNickname())) {
      throw new IllegalStateException("이미 사용 중인 닉네임입니다. ");
    }

    Users user =
        usersRepository
            .findByUid(signupRequestDto.getUid())
            .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다. "));

    user.completeSignup(signupRequestDto);
    usersRepository.save(user);

    for (String alertKeyword : signupRequestDto.getAlertKeywordList()) {
      userAlertKeywordRepository.save(new UserAlertKeyword(user, alertKeyword));
    }

    // 추천 저장
    List<Long> recommendIds =
        Optional.ofNullable(signupRequestDto.getRecommendList()).orElseGet(List::of).stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (!recommendIds.isEmpty()) {
      List<Recommend> recommendList = recommendRepository.findAllById(recommendIds);
      for (Recommend recommend : recommendList) {
        userRecommendRepository.save(new UserRecommend(user, recommend));
      }
    }
    emailService.sendNewUserSignUpMail(user);

    return SignupResponseDto.from(user);
  }

  // update + insert (존재하면 값 반환, 없으면 insert 후 반환)
  private Users upsertByUid(String uid, String email) {
    return usersRepository
        .findByUid(uid)
        .orElseGet(
            () ->
                usersRepository.save(
                    Users.builder()
                        .uid(uid)
                        .provider(Provider.GOOGLE)
                        .role(Role.MEMBER)
                        .email(email)
                        .build()));
  }

  // 1. code -> 토큰
  private GoogleTokenResponseDto getAccessToken(String authorizationCode) {
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("grant_type", "authorization_code");
      form.add("code", authorizationCode);
      form.add("client_id", googleProperties.getClientId());
      form.add("client_secret", googleProperties.getClientSecret());
      form.add("redirect_uri", googleProperties.getRedirectUri());

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      ResponseEntity<GoogleTokenResponseDto> res =
          restTemplate.exchange(
              googleProperties.getTokenUri(),
              HttpMethod.POST,
              new HttpEntity<>(form, headers),
              GoogleTokenResponseDto.class);
      GoogleTokenResponseDto body = res.getBody();
      if (body == null || body.getAccessToken() == null) {
        throw new IllegalStateException("Google token exchange failed (empty response)");
      }
      return body;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to exchange Google token", e);
    }
  }

  // 2. 토큰 -> user info
  private GoogleUserInfoResponseDto getUserInfo(String accessToken) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(accessToken);
      headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

      ResponseEntity<GoogleUserInfoResponseDto> res =
          restTemplate.exchange(
              googleProperties.getUserInfoUri(),
              HttpMethod.GET,
              new HttpEntity<>(headers),
              GoogleUserInfoResponseDto.class);
      GoogleUserInfoResponseDto body = res.getBody();
      if (body == null || body.getSub() == null) {
        throw new IllegalStateException("Google userinfo failed (empty sub)");
      }
      return body;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to fetch Google userinfo", e);
    }
  }
}
