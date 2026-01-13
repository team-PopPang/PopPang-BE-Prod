package com.poppang.be.domain.auth.apple.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.poppang.be.common.mail.EmailService;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import com.poppang.be.domain.auth.apple.dto.request.AppleAppLoginRequestDto;
import com.poppang.be.domain.auth.apple.dto.response.AppleTokenResponseDto;
import com.poppang.be.domain.auth.apple.dto.response.AppleUserInfoResponseDto;
import com.poppang.be.domain.auth.apple.util.AppleJwtUtil;
import com.poppang.be.domain.auth.apple.util.AppleJwtVerifier;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
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
import java.text.ParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AppleAuthServiceImpl implements AppleAuthService {

  private final AppleProperties appleProperties;
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

    AppleTokenResponseDto appleToken = getAccessToken(authCode);
    if (appleToken == null
        || appleToken.getAccessToken() == null
        || appleToken.getAccessToken().isBlank()) {
      throw new IllegalStateException("Failed to retrieve Apple access token");
    }

    AppleUserInfoResponseDto appleUserInfoResponseDto =
        parseIdTokenToProfile(appleToken.getIdToken());
    String uid = appleUserInfoResponseDto.getUid();

    Users user = upsertByUid(uid, appleUserInfoResponseDto.getEmail());

    return LoginResponseDto.from(user);
  }

  // App 로그인
  @Override
  @Transactional
  public LoginResponseDto mobileLogin(AppleAppLoginRequestDto appleAppLoginRequestDto) {

    AppleTokenResponseDto appleToken = getAccessToken(appleAppLoginRequestDto.getAuthCode());
    if (appleToken == null
        || appleToken.getAccessToken() == null
        || appleToken.getAccessToken().isBlank()) {
      throw new IllegalStateException("Failed to retrieve Apple access token");
    }

    AppleUserInfoResponseDto appleUserInfoResponseDto =
        parseIdTokenToProfile(appleToken.getIdToken());
    String uid = appleUserInfoResponseDto.getUid();

    Users user = upsertByUid(uid, appleAppLoginRequestDto.getEmail());

    return LoginResponseDto.from(user);
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

    // 키워드 저장
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
                        .provider(Provider.APPLE)
                        .role(Role.MEMBER)
                        .email(email)
                        .build()));
  }

  // 1. code -> access_token + id_token
  private AppleTokenResponseDto getAccessToken(String code) {

    try {
      String clientSecret = AppleJwtUtil.createClientSecret(appleProperties);

      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("client_id", appleProperties.getClientId());
      params.add("client_secret", clientSecret);
      params.add("code", code);
      params.add("grant_type", "authorization_code");
      params.add("redirect_uri", appleProperties.getRedirectUri());

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

      ResponseEntity<AppleTokenResponseDto> response =
          restTemplate.exchange(
              appleProperties.getTokenUri(), HttpMethod.POST, request, AppleTokenResponseDto.class);

      AppleTokenResponseDto tokenResponse = response.getBody();
      if (tokenResponse == null) {
        throw new IllegalStateException(" Apple token exchange failed (response null)");
      }
      return tokenResponse;

    } catch (RestClientException e) {
      throw new IllegalStateException("Failed to call Apple token endpoint", e);
    } catch (JOSEException | ParseException e) { // createClientSecret 등에서 던질 수 있는 구체 예외
      throw new IllegalStateException("Failed to create Apple client_secret", e);
    } catch (Exception e) {
      throw new IllegalStateException("Unexpected error", e);
    }
  }

  // 2. token(id_token) 검증 + id_token -> user info
  private AppleUserInfoResponseDto parseIdTokenToProfile(String idToken) {
    try {
      JWTClaimsSet claims = AppleJwtVerifier.verifyIdToken(idToken, appleProperties.getClientId());
      // user info 추출
      String sub = claims.getSubject();
      String uid = sub;
      String email = claims.getStringClaim("email");

      return new AppleUserInfoResponseDto(sub, uid, email);

    } catch (Exception e) {
      throw new IllegalStateException("Invalid Apple id_token", e);
    }
  }
}
