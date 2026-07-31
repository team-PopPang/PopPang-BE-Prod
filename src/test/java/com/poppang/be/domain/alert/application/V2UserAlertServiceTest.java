package com.poppang.be.domain.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.dto.v2.V2UserAlertResponseDto;
import com.poppang.be.domain.alert.entity.UserAlert;
import com.poppang.be.domain.alert.infrastructure.UserAlertRepository;
import com.poppang.be.domain.favorite.application.V2FavoritePopupResponseDtoMapper;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.favorite.entity.UserFavorite;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2UserAlertServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private UsersRepository usersRepository;
  @Mock private PopupRepository popupRepository;
  @Mock private UserAlertRepository userAlertRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private V2FavoritePopupResponseDtoMapper popupResponseMapper;

  private V2UserAlertServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new V2UserAlertServiceImpl(
            usersRepository,
            popupRepository,
            userAlertRepository,
            userFavoriteRepository,
            popupResponseMapper);
  }

  @Test
  void getUserAlertPopupListUsesThePrincipalUserAndKeepsOrderAndReadState() {
    Users user = user();
    Popup popup = popup();
    UserAlert alert = alert(user, popup, LocalDateTime.parse("2026-07-31T10:00:00"));
    UserFavorite favorite = new UserFavorite(user, popup);
    V2FavoritePopupResponseDto popupResponse = popupResponse();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(userAlertRepository.findAllByUserIdOrderByAlertedAtDesc(user.getId()))
        .willReturn(List.of(alert));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID))
        .willReturn(List.of(favorite));
    given(popupResponseMapper.toResponseList(List.of(popup), Set.of(popup.getId())))
        .willReturn(List.of(popupResponse));

    List<V2UserAlertResponseDto> responses = service.getUserAlertPopupList(USER_UUID);

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).popupUuid()).isEqualTo(POPUP_UUID);
    assertThat(responses.get(0).favorited()).isTrue();
    assertThat(responses.get(0).read()).isTrue();
    verify(userAlertRepository).findAllByUserIdOrderByAlertedAtDesc(user.getId());
    verify(userFavoriteRepository).findAllActivatedByUserUuid(USER_UUID);
  }

  @Test
  void getUserAlertPopupListReturnsEmptyWithoutLoadingFavoriteOrPopupDetails() {
    Users user = user();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(userAlertRepository.findAllByUserIdOrderByAlertedAtDesc(user.getId()))
        .willReturn(List.of());

    assertThat(service.getUserAlertPopupList(USER_UUID)).isEmpty();

    verifyNoInteractions(userFavoriteRepository, popupResponseMapper);
  }

  @Test
  void deleteUserAlertUsesThePrincipalUserAndPopupTarget() {
    Users user = user();
    Popup popup = popup();
    UserAlert alert = alert(user, popup, null);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(userAlertRepository.findByUser_IdAndPopup_Id(user.getId(), popup.getId()))
        .willReturn(Optional.of(alert));

    service.deleteUserAlert(USER_UUID, POPUP_UUID);

    verify(userAlertRepository).findByUser_IdAndPopup_Id(user.getId(), popup.getId());
    verify(userAlertRepository).delete(alert);
  }

  @Test
  void readUserAlertUsesThePrincipalUserAndMarksOnlyUnreadAlerts() {
    Users user = user();
    Popup popup = popup();
    UserAlert alert = alert(user, popup, null);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(userAlertRepository.findByUser_IdAndPopup_Id(user.getId(), popup.getId()))
        .willReturn(Optional.of(alert));

    service.readUserAlertPopup(USER_UUID, POPUP_UUID);

    assertThat(alert.getReadAt()).isNotNull();
    verify(userAlertRepository).findByUser_IdAndPopup_Id(user.getId(), popup.getId());
  }

  @Test
  void readUserAlertKeepsAnAlreadyReadAlertUnchanged() {
    Users user = user();
    Popup popup = popup();
    LocalDateTime readAt = LocalDateTime.parse("2026-07-31T09:00:00");
    UserAlert alert = spy(alert(user, popup, readAt));
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(userAlertRepository.findByUser_IdAndPopup_Id(user.getId(), popup.getId()))
        .willReturn(Optional.of(alert));

    service.readUserAlertPopup(USER_UUID, POPUP_UUID);

    assertThat(alert.getReadAt()).isEqualTo(readAt);
    verify(alert, never()).markAsRead();
  }

  @Test
  void deleteAndReadRejectMissingOrBlankPopupUuidBeforeRepositoryAccess() {
    for (String invalidPopupUuid : new String[] {null, "", "   "}) {
      assertError(
          () -> service.deleteUserAlert(USER_UUID, invalidPopupUuid),
          ErrorCode.INVALID_USER_REQUEST);
      assertError(
          () -> service.readUserAlertPopup(USER_UUID, invalidPopupUuid),
          ErrorCode.INVALID_USER_REQUEST);
    }

    verifyNoInteractions(
        usersRepository,
        popupRepository,
        userAlertRepository,
        userFavoriteRepository,
        popupResponseMapper);
  }

  @Test
  void deleteAndReadKeepTheLegacyNotFoundErrors() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.empty());
    assertError(() -> service.deleteUserAlert(USER_UUID, POPUP_UUID), ErrorCode.USER_NOT_FOUND);

    Users user = user();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.empty());
    assertError(() -> service.readUserAlertPopup(USER_UUID, POPUP_UUID), ErrorCode.POPUP_NOT_FOUND);

    Popup popup = popup();
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(userAlertRepository.findByUser_IdAndPopup_Id(user.getId(), popup.getId()))
        .willReturn(Optional.empty());
    assertError(
        () -> service.readUserAlertPopup(USER_UUID, POPUP_UUID), ErrorCode.USER_ALERT_NOT_FOUND);
  }

  private Users user() {
    return Users.builder().id(1L).uuid(USER_UUID).build();
  }

  private Popup popup() {
    return Popup.builder().id(2L).uuid(POPUP_UUID).activated(true).build();
  }

  private UserAlert alert(Users user, Popup popup, LocalDateTime readAt) {
    return UserAlert.builder()
        .user(user)
        .popup(popup)
        .alertedAt(LocalDateTime.parse("2026-07-31T08:00:00"))
        .readAt(readAt)
        .build();
  }

  private V2FavoritePopupResponseDto popupResponse() {
    return new V2FavoritePopupResponseDto(
        POPUP_UUID,
        "팝업",
        null,
        null,
        null,
        null,
        "주소",
        null,
        "서울",
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        null,
        10,
        20,
        true);
  }

  private void assertError(Runnable operation, ErrorCode expected) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
  }
}
