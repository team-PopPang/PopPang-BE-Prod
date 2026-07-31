package com.poppang.be.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtProperties;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.jwt.VerifiedJwt;
import com.poppang.be.common.ratelimit.V2AuthRateLimitScope;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import com.poppang.be.domain.auth.redis.TokenHashRecord;
import com.poppang.be.domain.auth.redis.V2RefreshTokenRedisRepository;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2TokenServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String SESSION_ID = "22222222-2222-2222-2222-222222222222";
  private static final String CURRENT_REFRESH = "current.refresh.token";
  private static final String NEW_ACCESS = "new.access.token";
  private static final String NEW_REFRESH = "new.refresh.token";
  private static final Instant ISSUED_AT = Instant.parse("2026-07-29T00:00:00Z");
  private static final JwtProperties JWT_PROPERTIES =
      new JwtProperties(
          "0123456789abcdef0123456789abcdef",
          15,
          30,
          "poppang",
          "poppang-app-v2",
          "poppang-signup-v2",
          15);

  @Mock private JwtProvider jwtProvider;
  @Mock private V2RefreshTokenRedisRepository refreshTokenRepository;
  @Mock private UsersRepository usersRepository;
  @Mock private V2AuthRateLimiter authRateLimiter;

  private V2TokenService tokenService;

  @BeforeEach
  void setUp() {
    tokenService =
        new V2TokenService(
            jwtProvider, JWT_PROPERTIES, refreshTokenRepository, usersRepository, authRateLimiter);
  }

  @Test
  void issueTokensCreatesOneSessionAndStoresOnlyTheRefreshFingerprint() {
    VerifiedJwt newRefresh =
        verified("new-refresh-jti", SESSION_ID, ISSUED_AT.plusSeconds(30 * 86400));
    when(jwtProvider.createSessionId()).thenReturn(SESSION_ID);
    stubNewTokenPair(newRefresh);

    V2TokenResponseDto response = tokenService.issueTokens(USER_UUID);

    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.accessToken()).isEqualTo(NEW_ACCESS);
    assertThat(response.refreshToken()).isEqualTo(NEW_REFRESH);
    assertThat(response.accessTokenExpiresIn()).isEqualTo(900);
    assertThat(response.refreshTokenExpiresIn()).isEqualTo(2_592_000);
    verify(jwtProvider).createAccessToken(USER_UUID, SESSION_ID);
    verify(jwtProvider).createRefreshToken(USER_UUID, SESSION_ID);

    ArgumentCaptor<TokenHashRecord> recordCaptor = ArgumentCaptor.forClass(TokenHashRecord.class);
    verify(refreshTokenRepository)
        .save(org.mockito.ArgumentMatchers.eq(USER_UUID), recordCaptor.capture());
    assertThat(recordCaptor.getValue().sessionId()).isEqualTo(SESSION_ID);
    assertThat(recordCaptor.getValue().tokenHash()).doesNotContain(NEW_REFRESH);
    assertThat(recordCaptor.getValue().toString()).doesNotContain(NEW_REFRESH);
  }

  @Test
  void refreshRotatesAtomicallyAndReturnsACompletePairWithTheSameSession() {
    VerifiedJwt current = verified("current-jti", SESSION_ID, ISSUED_AT.plusSeconds(3600));
    VerifiedJwt replacement =
        verified("replacement-jti", SESSION_ID, ISSUED_AT.plusSeconds(30 * 86400));
    when(jwtProvider.verify(CURRENT_REFRESH, JwtTokenType.REFRESH)).thenReturn(current);
    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(user(false, SignupStatus.COMPLETED)));
    stubNewTokenPair(replacement);
    when(refreshTokenRepository.rotate(any(), any(), any())).thenReturn(true);

    V2TokenResponseDto response = tokenService.refresh(CURRENT_REFRESH);

    assertThat(response.accessToken()).isEqualTo(NEW_ACCESS);
    assertThat(response.refreshToken()).isEqualTo(NEW_REFRESH);
    verify(jwtProvider).createAccessToken(USER_UUID, SESSION_ID);
    verify(jwtProvider).createRefreshToken(USER_UUID, SESSION_ID);

    ArgumentCaptor<TokenHashRecord> currentCaptor = ArgumentCaptor.forClass(TokenHashRecord.class);
    ArgumentCaptor<TokenHashRecord> replacementCaptor =
        ArgumentCaptor.forClass(TokenHashRecord.class);
    verify(refreshTokenRepository)
        .rotate(
            org.mockito.ArgumentMatchers.eq(USER_UUID),
            currentCaptor.capture(),
            replacementCaptor.capture());
    assertThat(currentCaptor.getValue().sessionId()).isEqualTo(SESSION_ID);
    assertThat(replacementCaptor.getValue().sessionId()).isEqualTo(SESSION_ID);
    assertThat(currentCaptor.getValue().tokenHash())
        .isNotEqualTo(replacementCaptor.getValue().tokenHash());
    verify(authRateLimiter).check(V2AuthRateLimitScope.REFRESH, USER_UUID);
  }

  @Test
  void refreshRateLimitStopsIssuanceAndRotation() {
    VerifiedJwt current = verified("current-jti", SESSION_ID, ISSUED_AT.plusSeconds(3600));
    when(jwtProvider.verify(CURRENT_REFRESH, JwtTokenType.REFRESH)).thenReturn(current);
    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(user(false, SignupStatus.COMPLETED)));
    doThrow(new BaseException(ErrorCode.RATE_LIMIT_EXCEEDED))
        .when(authRateLimiter)
        .check(V2AuthRateLimitScope.REFRESH, USER_UUID);

    assertError(() -> tokenService.refresh(CURRENT_REFRESH), ErrorCode.RATE_LIMIT_EXCEEDED);

    verify(jwtProvider, never()).createAccessToken(any(), any());
    verify(jwtProvider, never()).createRefreshToken(any(), any());
    verify(refreshTokenRepository, never()).rotate(any(), any(), any());
  }

  @Test
  void refreshMismatchFailsWithoutDeletingTheCurrentSession() {
    VerifiedJwt current = verified("current-jti", SESSION_ID, ISSUED_AT.plusSeconds(3600));
    VerifiedJwt replacement =
        verified("replacement-jti", SESSION_ID, ISSUED_AT.plusSeconds(30 * 86400));
    when(jwtProvider.verify(CURRENT_REFRESH, JwtTokenType.REFRESH)).thenReturn(current);
    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(user(false, SignupStatus.COMPLETED)));
    stubNewTokenPair(replacement);
    when(refreshTokenRepository.rotate(any(), any(), any())).thenReturn(false);

    assertError(() -> tokenService.refresh(CURRENT_REFRESH), ErrorCode.REFRESH_TOKEN_MISMATCH);

    verify(refreshTokenRepository, never()).deleteIfSessionMatches(any(), any());
  }

  @Test
  void refreshRejectsBlankMissingDeletedAndPendingAccountsBeforeRotation() {
    assertError(() -> tokenService.refresh("  "), ErrorCode.INVALID_REFRESH_REQUEST);
    verifyNoInteractions(jwtProvider, refreshTokenRepository, usersRepository);

    VerifiedJwt current = verified("current-jti", SESSION_ID, ISSUED_AT.plusSeconds(3600));
    when(jwtProvider.verify(CURRENT_REFRESH, JwtTokenType.REFRESH)).thenReturn(current);

    when(usersRepository.findByUuid(USER_UUID)).thenReturn(Optional.empty());
    assertError(() -> tokenService.refresh(CURRENT_REFRESH), ErrorCode.ACCOUNT_NOT_ACTIVE);

    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(user(true, SignupStatus.COMPLETED)));
    assertError(() -> tokenService.refresh(CURRENT_REFRESH), ErrorCode.ACCOUNT_NOT_ACTIVE);

    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(user(false, SignupStatus.PENDING)));
    assertError(() -> tokenService.refresh(CURRENT_REFRESH), ErrorCode.INSUFFICIENT_AUTHORITY);

    verify(refreshTokenRepository, never()).rotate(any(), any(), any());
  }

  @Test
  void refreshPropagatesAuthenticationStoreOutage() {
    VerifiedJwt current = verified("current-jti", SESSION_ID, ISSUED_AT.plusSeconds(3600));
    VerifiedJwt replacement =
        verified("replacement-jti", SESSION_ID, ISSUED_AT.plusSeconds(30 * 86400));
    when(jwtProvider.verify(CURRENT_REFRESH, JwtTokenType.REFRESH)).thenReturn(current);
    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(user(false, SignupStatus.COMPLETED)));
    stubNewTokenPair(replacement);
    when(refreshTokenRepository.rotate(any(), any(), any()))
        .thenThrow(new BaseException(ErrorCode.AUTH_STORE_UNAVAILABLE));

    assertError(() -> tokenService.refresh(CURRENT_REFRESH), ErrorCode.AUTH_STORE_UNAVAILABLE);
  }

  @Test
  void logoutIsIdempotentForMissingOrChangedSessions() {
    when(refreshTokenRepository.deleteIfSessionMatches(USER_UUID, SESSION_ID))
        .thenReturn(true, false);

    tokenService.logout(USER_UUID, SESSION_ID);
    tokenService.logout(USER_UUID, SESSION_ID);

    verify(refreshTokenRepository, org.mockito.Mockito.times(2))
        .deleteIfSessionMatches(USER_UUID, SESSION_ID);
  }

  @Test
  void logoutDoesNotHideAuthenticationStoreOutage() {
    when(refreshTokenRepository.deleteIfSessionMatches(USER_UUID, SESSION_ID))
        .thenThrow(new BaseException(ErrorCode.AUTH_STORE_UNAVAILABLE));

    assertError(() -> tokenService.logout(USER_UUID, SESSION_ID), ErrorCode.AUTH_STORE_UNAVAILABLE);
  }

  private void stubNewTokenPair(VerifiedJwt newRefresh) {
    when(jwtProvider.createAccessToken(USER_UUID, SESSION_ID)).thenReturn(NEW_ACCESS);
    when(jwtProvider.createRefreshToken(USER_UUID, SESSION_ID)).thenReturn(NEW_REFRESH);
    when(jwtProvider.verify(NEW_REFRESH, JwtTokenType.REFRESH)).thenReturn(newRefresh);
  }

  private VerifiedJwt verified(String jwtId, String sessionId, Instant expiresAt) {
    return new VerifiedJwt(
        USER_UUID, JwtTokenType.REFRESH, "poppang-app-v2", ISSUED_AT, expiresAt, jwtId, sessionId);
  }

  private Users user(boolean deleted, SignupStatus signupStatus) {
    return Users.builder()
        .uuid(USER_UUID)
        .role(Role.MEMBER)
        .signupStatus(signupStatus)
        .deleted(deleted)
        .build();
  }

  private void assertError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }
}
