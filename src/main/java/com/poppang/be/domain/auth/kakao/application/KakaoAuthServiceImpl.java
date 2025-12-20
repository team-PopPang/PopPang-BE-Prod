package com.poppang.be.domain.auth.kakao.application;

import com.poppang.be.common.enums.Role;
import com.poppang.be.common.mail.EmailService;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
import com.poppang.be.domain.auth.kakao.config.KakaoProperties;
import com.poppang.be.domain.auth.kakao.dto.request.KakaoAppLoginRequestDto;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;
import com.poppang.be.domain.auth.kakao.dto.response.KakaoTokenResponseDto;
import com.poppang.be.domain.auth.kakao.dto.response.KakaoUserInfoResponseDto;
import com.poppang.be.domain.keyword.entity.UserAlertKeyword;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Provider;
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
public class KakaoAuthServiceImpl implements KakaoAuthService {

  private final KakaoProperties kakaoProperties;
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
    KakaoTokenResponseDto kakaoToken = getAccessToken(authCode);
    if (kakaoToken == null
        || kakaoToken.getAccessToken() == null
        || kakaoToken.getAccessToken().isBlank()) {
      throw new IllegalStateException("Failed to retrieve Kakao access token");
    }

    KakaoUserInfoResponseDto kakaoUserInfoResponseDto = getUserInfo(kakaoToken.getAccessToken());
    String uid = String.valueOf(kakaoUserInfoResponseDto.getId());

    Users user = upsertByUid(uid);

    return LoginResponseDto.from(user);
  }

  // App 로그인
  @Override
  @Transactional
  public LoginResponseDto mobileLogin(KakaoAppLoginRequestDto kakaoAppLoginRequestDto) {

    KakaoUserInfoResponseDto kakaoUserInfoResponseDto =
        getUserInfo(kakaoAppLoginRequestDto.getAccessToken());
    String uid = String.valueOf(kakaoUserInfoResponseDto.getId());

    Users user = upsertByUid(uid);

    return LoginResponseDto.from(user);
  }

  // 회원가입
  @Override
  @Transactional
  public SignupResponseDto signup(SignupRequestDto signupRequestDto) {

    // 닉네임 중복 확인
    if (usersRepository.existsByNickname(signupRequestDto.getNickname())) {
      throw new IllegalArgumentException("이미 사용 중인 닉네임입니다. ");
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
  private Users upsertByUid(String uid) {
    return usersRepository
        .findByUid(uid)
        .orElseGet(
            () ->
                usersRepository.save(
                    Users.builder().uid((uid)).provider(Provider.KAKAO).role(Role.MEMBER).build()));
  }

  // 1. code -> 토큰
  private KakaoTokenResponseDto getAccessToken(String code) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("client_id", kakaoProperties.getClientId());
    params.add("redirect_uri", kakaoProperties.getRedirectUri());
    params.add("code", code);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

    ResponseEntity<KakaoTokenResponseDto> response =
        restTemplate.exchange(
            kakaoProperties.getTokenUri(), HttpMethod.POST, request, KakaoTokenResponseDto.class);

    return response.getBody();
  }

  // 2. 토큰 -> user info
  private KakaoUserInfoResponseDto getUserInfo(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    HttpEntity<Void> req = new HttpEntity<>(headers);

    ResponseEntity<KakaoUserInfoResponseDto> res =
        restTemplate.exchange(
            "https://kapi.kakao.com/v2/user/me",
            HttpMethod.GET,
            req,
            KakaoUserInfoResponseDto.class);

    return res.getBody();
  }
}
