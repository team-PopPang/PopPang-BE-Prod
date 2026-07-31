package com.poppang.be.domain.auth.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.auth.redis.TokenHashRecord;
import com.poppang.be.domain.auth.redis.V2SignupTokenRedisRepository;
import com.poppang.be.domain.keyword.entity.UserAlertKeyword;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class V2SocialSignupWriter {

  private final UsersRepository usersRepository;
  private final UserAlertKeywordRepository userAlertKeywordRepository;
  private final UserRecommendRepository userRecommendRepository;
  private final RecommendRepository recommendRepository;
  private final V2SignupTokenRedisRepository signupTokenRepository;

  @Transactional
  V2AuthUserResponseDto completeSignup(
      Provider expectedProvider,
      String userUuid,
      V2SignupRequestDto request,
      JwtAuthenticationDetails details) {
    Users user =
        usersRepository
            .findByUuidForUpdate(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE));
    if (user.isDeleted()) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    if (user.getSignupStatus() == SignupStatus.COMPLETED) {
      throw new BaseException(ErrorCode.SIGNUP_ALREADY_COMPLETED);
    }
    if (user.getSignupStatus() != SignupStatus.PENDING) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    if (user.getProvider() != expectedProvider) {
      throw new BaseException(ErrorCode.SIGNUP_PROVIDER_MISMATCH);
    }

    ValidatedSignupInput input = validate(request);
    if (usersRepository.existsByNickname(input.nickname())) {
      throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
    }

    List<Recommend> recommends =
        input.recommendIds().isEmpty()
            ? List.of()
            : recommendRepository.findAllById(input.recommendIds());
    if (recommends.size() != input.recommendIds().size()) {
      throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
    }

    TokenHashRecord signupRecord =
        new TokenHashRecord(
            details.tokenFingerprint(),
            details.jwtId(),
            null,
            details.issuedAt(),
            details.expiresAt());
    if (!signupTokenRepository.consume(userUuid, signupRecord)) {
      throw new BaseException(ErrorCode.SIGNUP_TOKEN_MISMATCH);
    }

    user.completeSignup(input.nickname(), request.alerted(), request.fcmToken());
    input
        .keywords()
        .forEach(keyword -> userAlertKeywordRepository.save(UserAlertKeyword.from(user, keyword)));
    recommends.forEach(
        recommend -> userRecommendRepository.save(new UserRecommend(user, recommend)));
    return V2AuthUserResponseDto.from(user);
  }

  private ValidatedSignupInput validate(V2SignupRequestDto request) {
    if (request == null || request.nickname() == null || request.nickname().isBlank()) {
      throw new BaseException(ErrorCode.INVALID_SIGNUP_REQUEST);
    }

    List<String> rawKeywords = Optional.ofNullable(request.alertKeywordList()).orElseGet(List::of);
    List<String> keywords =
        rawKeywords.stream()
            .map(keyword -> keyword == null ? null : keyword.trim())
            .filter(Objects::nonNull)
            .toList();
    if (keywords.size() != rawKeywords.size() || keywords.stream().anyMatch(String::isBlank)) {
      throw new BaseException(ErrorCode.INVALID_SIGNUP_REQUEST);
    }
    keywords = keywords.stream().distinct().toList();

    List<Long> rawRecommendIds = Optional.ofNullable(request.recommendList()).orElseGet(List::of);
    if (rawRecommendIds.stream().anyMatch(Objects::isNull)) {
      throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
    }
    List<Long> recommendIds = rawRecommendIds.stream().distinct().toList();

    return new ValidatedSignupInput(request.nickname(), keywords, recommendIds);
  }

  private record ValidatedSignupInput(
      String nickname, List<String> keywords, List<Long> recommendIds) {}
}
