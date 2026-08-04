package com.poppang.be.domain.alert.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.dto.v2.V2WorkerUserAlertRegisterRequestDto;
import com.poppang.be.domain.alert.entity.UserAlert;
import com.poppang.be.domain.alert.infrastructure.UserAlertRepository;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2InternalUserAlertServiceImplTest {

  @Mock private UserAlertRepository userAlertRepository;
  @Mock private UsersRepository usersRepository;
  @Mock private PopupRepository popupRepository;

  @InjectMocks private V2InternalUserAlertServiceImpl userAlertService;

  @Test
  void registerAlertTreatsPathUuidAsRecipientAndPreservesLegacyStoredRelationship() {
    Users user = Users.builder().id(3L).uuid("recipient-uuid").build();
    Popup popup = Popup.builder().id(7L).uuid("popup-uuid").build();
    when(usersRepository.findByUuid("recipient-uuid")).thenReturn(Optional.of(user));
    when(popupRepository.findByUuid("popup-uuid")).thenReturn(Optional.of(popup));

    userAlertService.registerUserAlert(
        "recipient-uuid", new V2WorkerUserAlertRegisterRequestDto("popup-uuid"));

    ArgumentCaptor<UserAlert> alertCaptor = ArgumentCaptor.forClass(UserAlert.class);
    verify(userAlertRepository).save(alertCaptor.capture());
    assertThat(alertCaptor.getValue().getUser()).isSameAs(user);
    assertThat(alertCaptor.getValue().getPopup()).isSameAs(popup);
    assertThat(alertCaptor.getValue().getAlertedAt()).isNotNull();
    assertThat(alertCaptor.getValue().getReadAt()).isNull();
  }

  @Test
  void registerAlertKeepsLegacyDuplicateError() {
    Users user = Users.builder().id(3L).uuid("recipient-uuid").build();
    Popup popup = Popup.builder().id(7L).uuid("popup-uuid").build();
    when(usersRepository.findByUuid("recipient-uuid")).thenReturn(Optional.of(user));
    when(popupRepository.findByUuid("popup-uuid")).thenReturn(Optional.of(popup));
    when(userAlertRepository.existsByUser_IdAndPopup_Id(3L, 7L)).thenReturn(true);

    assertThatThrownBy(
            () ->
                userAlertService.registerUserAlert(
                    "recipient-uuid", new V2WorkerUserAlertRegisterRequestDto("popup-uuid")))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_ALERT_ALREADY_EXISTS);

    verify(userAlertRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void registerAlertKeepsLegacyUserAndPopupNotFoundErrors() {
    when(usersRepository.findByUuid("missing-user")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                userAlertService.registerUserAlert(
                    "missing-user", new V2WorkerUserAlertRegisterRequestDto("popup-uuid")))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    Users user = Users.builder().id(3L).uuid("recipient-uuid").build();
    when(usersRepository.findByUuid("recipient-uuid")).thenReturn(Optional.of(user));
    when(popupRepository.findByUuid("missing-popup")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                userAlertService.registerUserAlert(
                    "recipient-uuid", new V2WorkerUserAlertRegisterRequestDto("missing-popup")))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.POPUP_NOT_FOUND);
  }
}
