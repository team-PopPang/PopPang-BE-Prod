package com.poppang.be.domain.auth.kakao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.redis.TokenHashRecord;
import com.poppang.be.domain.auth.redis.V2SignupTokenRedisRepository;
import com.poppang.be.domain.keyword.entity.UserAlertKeyword;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class V2KakaoSignupWriterTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  private final UsersRepository usersRepository = Mockito.mock(UsersRepository.class);
  private final UserAlertKeywordRepository keywordRepository =
      Mockito.mock(UserAlertKeywordRepository.class);
  private final UserRecommendRepository userRecommendRepository =
      Mockito.mock(UserRecommendRepository.class);
  private final RecommendRepository recommendRepository = Mockito.mock(RecommendRepository.class);
  private final V2SignupTokenRedisRepository signupTokenRepository =
      Mockito.mock(V2SignupTokenRedisRepository.class);
  private final V2KakaoSignupWriter writer =
      new V2KakaoSignupWriter(
          usersRepository,
          keywordRepository,
          userRecommendRepository,
          recommendRepository,
          signupTokenRepository);

  @Test
  void consumesLatestTokenThenCompletesUserAndStoresValidatedRelations() {
    Users user = pendingKakaoUser();
    Recommend recommend = Mockito.mock(Recommend.class);
    V2SignupRequestDto request =
        new V2SignupRequestDto(
            "nickname", true, "fcm-token", List.of("전시", "전시", "패션"), List.of(1L, 1L));
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));
    given(usersRepository.existsByNickname("nickname")).willReturn(false);
    given(recommendRepository.findAllById(List.of(1L))).willReturn(List.of(recommend));
    given(signupTokenRepository.consume(any(), any())).willReturn(true);

    var response = writer.completeSignup(USER_UUID, request, details());

    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(user.getNickname()).isEqualTo("nickname");
    assertThat(user.isAlerted()).isTrue();
    assertThat(user.getFcmToken()).isEqualTo("fcm-token");
    assertThat(response.userUuid()).isEqualTo(USER_UUID);

    ArgumentCaptor<TokenHashRecord> tokenRecord = ArgumentCaptor.forClass(TokenHashRecord.class);
    verify(signupTokenRepository)
        .consume(org.mockito.ArgumentMatchers.eq(USER_UUID), tokenRecord.capture());
    assertThat(tokenRecord.getValue().jwtId()).isEqualTo("jwt-id");
    assertThat(tokenRecord.getValue().tokenHash()).isEqualTo("a".repeat(64));

    ArgumentCaptor<UserAlertKeyword> keywords = ArgumentCaptor.forClass(UserAlertKeyword.class);
    verify(keywordRepository, org.mockito.Mockito.times(2)).save(keywords.capture());
    assertThat(keywords.getAllValues())
        .extracting(UserAlertKeyword::getAlertKeyword)
        .containsExactly("전시", "패션");
    verify(userRecommendRepository).save(any(UserRecommend.class));
  }

  @Test
  void validatesNicknameBeforeConsumingToken() {
    given(usersRepository.findByUuidForUpdate(USER_UUID))
        .willReturn(Optional.of(pendingKakaoUser()));

    assertThatThrownBy(
            () ->
                writer.completeSignup(
                    USER_UUID,
                    new V2SignupRequestDto(" ", false, null, List.of(), List.of()),
                    details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_SIGNUP_REQUEST);

    verify(signupTokenRepository, never()).consume(any(), any());
  }

  @Test
  void rejectsDuplicateNicknameBeforeConsumingToken() {
    given(usersRepository.findByUuidForUpdate(USER_UUID))
        .willReturn(Optional.of(pendingKakaoUser()));
    given(usersRepository.existsByNickname("nickname")).willReturn(true);

    assertThatThrownBy(() -> writer.completeSignup(USER_UUID, validRequest(), details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

    verify(signupTokenRepository, never()).consume(any(), any());
  }

  @Test
  void rejectsInvalidRecommendSetBeforeConsumingToken() {
    given(usersRepository.findByUuidForUpdate(USER_UUID))
        .willReturn(Optional.of(pendingKakaoUser()));
    given(usersRepository.existsByNickname("nickname")).willReturn(false);
    given(recommendRepository.findAllById(List.of(1L, 2L)))
        .willReturn(List.of(Mockito.mock(Recommend.class)));

    assertThatThrownBy(
            () ->
                writer.completeSignup(
                    USER_UUID,
                    new V2SignupRequestDto("nickname", false, null, List.of(), List.of(1L, 2L)),
                    details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_RECOMMEND_ID);

    verify(signupTokenRepository, never()).consume(any(), any());
  }

  @Test
  void rejectsProviderMismatchAndAlreadyCompletedUsers() {
    Users google =
        Users.builder()
            .uuid(USER_UUID)
            .provider(Provider.GOOGLE)
            .role(Role.MEMBER)
            .signupStatus(SignupStatus.PENDING)
            .build();
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(google));

    assertThatThrownBy(() -> writer.completeSignup(USER_UUID, validRequest(), details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SIGNUP_PROVIDER_MISMATCH);

    Users completed =
        Users.builder()
            .uuid(USER_UUID)
            .provider(Provider.KAKAO)
            .role(Role.MEMBER)
            .signupStatus(SignupStatus.COMPLETED)
            .build();
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(completed));

    assertThatThrownBy(() -> writer.completeSignup(USER_UUID, validRequest(), details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SIGNUP_ALREADY_COMPLETED);
  }

  @Test
  void missingOrDeletedAccountCannotCompleteSignup() {
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.empty());
    assertThatThrownBy(() -> writer.completeSignup(USER_UUID, validRequest(), details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE);

    Users deleted =
        Users.builder()
            .uuid(USER_UUID)
            .provider(Provider.KAKAO)
            .role(Role.MEMBER)
            .signupStatus(SignupStatus.PENDING)
            .deleted(true)
            .build();
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(deleted));
    assertThatThrownBy(() -> writer.completeSignup(USER_UUID, validRequest(), details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE);
  }

  @Test
  void latestTokenMismatchDoesNotModifyUserOrRelations() {
    Users user = pendingKakaoUser();
    given(usersRepository.findByUuidForUpdate(USER_UUID)).willReturn(Optional.of(user));
    given(usersRepository.existsByNickname("nickname")).willReturn(false);
    given(signupTokenRepository.consume(any(), any())).willReturn(false);

    assertThatThrownBy(() -> writer.completeSignup(USER_UUID, validRequest(), details()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SIGNUP_TOKEN_MISMATCH);

    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.PENDING);
    verify(keywordRepository, never()).save(any());
    verify(userRecommendRepository, never()).save(any());
  }

  private Users pendingKakaoUser() {
    return Users.builder()
        .uuid(USER_UUID)
        .provider(Provider.KAKAO)
        .role(Role.MEMBER)
        .signupStatus(SignupStatus.PENDING)
        .deleted(false)
        .build();
  }

  private V2SignupRequestDto validRequest() {
    return new V2SignupRequestDto("nickname", false, null, List.of(), List.of());
  }

  private JwtAuthenticationDetails details() {
    return new JwtAuthenticationDetails(
        "a".repeat(64),
        "jwt-id",
        Instant.parse("2026-07-29T00:00:00Z"),
        Instant.parse("2026-07-29T00:15:00Z"));
  }
}
