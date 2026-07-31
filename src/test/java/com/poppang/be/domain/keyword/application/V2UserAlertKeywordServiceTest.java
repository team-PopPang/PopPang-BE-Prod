package com.poppang.be.domain.keyword.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.keyword.entity.UserAlertKeyword;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2UserAlertKeywordServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String KEYWORD = "성수";

  @Mock private UserAlertKeywordRepository userAlertKeywordRepository;
  @Mock private UsersRepository usersRepository;

  private V2UserAlertKeywordServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new V2UserAlertKeywordServiceImpl(userAlertKeywordRepository, usersRepository);
  }

  @Test
  void getUserAlertKeywordsUsesOnlyThePrincipalUserUuid() {
    given(userAlertKeywordRepository.findAllByUserUuid(USER_UUID))
        .willReturn(
            List.of(
                UserAlertKeyword.builder().alertKeyword("성수").build(),
                UserAlertKeyword.builder().alertKeyword("캐릭터").build()));

    assertThat(service.getUserAlertKeywords(USER_UUID))
        .extracting(response -> response.alertKeyword())
        .containsExactly("성수", "캐릭터");
    verify(userAlertKeywordRepository).findAllByUserUuid(USER_UUID);
  }

  @Test
  void registerAlertKeywordUsesThePrincipalUserAndKeywordTarget() {
    Users user = Users.builder().uuid(USER_UUID).build();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));

    service.registerAlertKeyword(USER_UUID, KEYWORD);

    ArgumentCaptor<UserAlertKeyword> keyword = ArgumentCaptor.forClass(UserAlertKeyword.class);
    verify(userAlertKeywordRepository).save(keyword.capture());
    assertThat(keyword.getValue().getUser()).isSameAs(user);
    assertThat(keyword.getValue().getAlertKeyword()).isEqualTo(KEYWORD);
  }

  @Test
  void registerAlertKeywordKeepsTheLegacyUserNotFoundError() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.empty());

    assertError(() -> service.registerAlertKeyword(USER_UUID, KEYWORD), ErrorCode.USER_NOT_FOUND);
    verify(userAlertKeywordRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void registerAndDeleteRejectMissingOrBlankKeywordsBeforeRepositoryAccess() {
    for (String invalidKeyword : new String[] {null, "", "   "}) {
      assertError(
          () -> service.registerAlertKeyword(USER_UUID, invalidKeyword),
          ErrorCode.INVALID_USER_REQUEST);
      assertError(
          () -> service.deleteAlertKeyword(USER_UUID, invalidKeyword),
          ErrorCode.INVALID_USER_REQUEST);
    }

    verifyNoInteractions(usersRepository, userAlertKeywordRepository);
  }

  @Test
  void deleteAlertKeywordUsesPrincipalUserUuidAndKeywordTarget() {
    Users user = Users.builder().uuid(USER_UUID).build();
    UserAlertKeyword keyword = UserAlertKeyword.builder().user(user).alertKeyword(KEYWORD).build();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(userAlertKeywordRepository.findByUserUuidAndAlertKeyword(USER_UUID, KEYWORD))
        .willReturn(Optional.of(keyword));

    service.deleteAlertKeyword(USER_UUID, KEYWORD);

    verify(userAlertKeywordRepository).delete(keyword);
  }

  @Test
  void deleteAlertKeywordKeepsLegacyUserAndKeywordNotFoundErrors() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.empty());
    assertError(() -> service.deleteAlertKeyword(USER_UUID, KEYWORD), ErrorCode.USER_NOT_FOUND);

    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    given(userAlertKeywordRepository.findByUserUuidAndAlertKeyword(USER_UUID, KEYWORD))
        .willReturn(Optional.empty());
    assertError(
        () -> service.deleteAlertKeyword(USER_UUID, KEYWORD), ErrorCode.ALERT_KEYWORD_NOT_FOUND);
  }

  private void assertError(Runnable operation, ErrorCode expected) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
  }
}
