package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.entity.UserAlert;
import com.poppang.be.domain.alert.infrastructure.UserAlertRepository;
import com.poppang.be.domain.popup.dto.app.request.PopupAlertTargetRequestDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.projection.PopupAlertTargetRow;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopupAlertTargetServiceTest {
  @Mock private PopupRepository popupRepository;
  @Mock private UserAlertRepository userAlertRepository;
  @Mock private UsersRepository usersRepository;
  @InjectMocks private PopupAlertTargetService service;

  @Test
  void emptyInputReturnsEmptyWithoutDatabaseAccess() {
    assertThat(service.findTargets(new PopupAlertTargetRequestDto(List.of()))).isEmpty();
    verifyNoInteractions(popupRepository, userAlertRepository, usersRepository);
  }

  @Test
  void missingBlankAndNullUuidsAreRejectedBeforeWriting() {
    for (var input :
        Arrays.asList(
            null,
            new PopupAlertTargetRequestDto(null),
            new PopupAlertTargetRequestDto(List.of(" ")),
            new PopupAlertTargetRequestDto(Arrays.asList("popup-1", null)))) {
      assertThatThrownBy(() -> service.findTargets(input))
          .isInstanceOf(BaseException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_POPUP_ALERT_TARGET_REQUEST);
    }
    verifyNoInteractions(popupRepository, userAlertRepository, usersRepository);
  }

  @Test
  void unknownPopupRejectsTheWholeRequestBeforeWritingAlerts() {
    when(popupRepository.findAllByUuidInForUpdate(List.of("missing"))).thenReturn(List.of());

    assertThatThrownBy(
            () -> service.findTargets(new PopupAlertTargetRequestDto(List.of("missing"))))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.POPUP_NOT_FOUND);
    verifyNoInteractions(userAlertRepository, usersRepository);
  }

  @Test
  void groupsUsersDeduplicatesAndPreservesRequestedPopupOrder() {
    var first = popup(1L, "popup-1");
    var second = popup(2L, "popup-2");
    when(popupRepository.findAllByUuidInForUpdate(List.of("popup-2", "popup-1")))
        .thenReturn(List.of(first, second));
    when(popupRepository.findAlertTargets(List.of("popup-2", "popup-1")))
        .thenReturn(
            List.of(
                row(7L, "token", 1L, "카페"),
                row(7L, "token", 1L, "성수"),
                row(7L, "token", 1L, "성수"),
                row(7L, "token", 2L, "카페")));
    when(usersRepository.getReferenceById(7L)).thenReturn(Users.builder().id(7L).build());

    var result =
        service.findTargets(
            new PopupAlertTargetRequestDto(List.of("popup-2", "popup-1", "popup-2")));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).userUuid()).isEqualTo("user-7");
    assertThat(result.get(0).fcmToken()).isEqualTo("token");
    assertThat(result.get(0).keywords()).containsExactly("카페", "성수");
    assertThat(result.get(0).popups())
        .extracting("popupUuid")
        .containsExactly("popup-2", "popup-1");
    ArgumentCaptor<List<UserAlert>> captor = ArgumentCaptor.forClass(List.class);
    verify(userAlertRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(captor.getValue())
        .allSatisfy(
            alert -> {
              assertThat(alert.getAlertedAt()).isNotNull();
              assertThat(alert.getReadAt()).isNull();
            });
  }

  @Test
  void existingAlertIsNotInsertedAgainButTargetRemainsInResponse() {
    when(popupRepository.findAllByUuidInForUpdate(List.of("popup-1")))
        .thenReturn(List.of(popup(1L, "popup-1")));
    when(popupRepository.findAlertTargets(List.of("popup-1")))
        .thenReturn(List.of(row(7L, "token", 1L, "카페")));
    var existing = mock(UserAlertRepository.AlertPair.class);
    when(existing.getUserId()).thenReturn(7L);
    when(existing.getPopupId()).thenReturn(1L);
    when(userAlertRepository.findExistingPairs(List.of(1L), List.of(7L)))
        .thenReturn(List.of(existing));

    assertThat(service.findTargets(new PopupAlertTargetRequestDto(List.of("popup-1")))).hasSize(1);
    verifyNoInteractions(usersRepository);
    verify(userAlertRepository, org.mockito.Mockito.never())
        .saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void blankTokenStillCreatesInboxAlertButIsNotReturnedForPush() {
    when(popupRepository.findAllByUuidInForUpdate(List.of("popup-1")))
        .thenReturn(List.of(popup(1L, "popup-1")));
    when(popupRepository.findAlertTargets(List.of("popup-1")))
        .thenReturn(List.of(row(7L, " ", 1L, "카페")));
    when(usersRepository.getReferenceById(7L)).thenReturn(Users.builder().id(7L).build());

    assertThat(service.findTargets(new PopupAlertTargetRequestDto(List.of("popup-1")))).isEmpty();
    verify(userAlertRepository).saveAll(org.mockito.ArgumentMatchers.any());
  }

  private Popup popup(Long id, String uuid) {
    return Popup.builder().id(id).uuid(uuid).name("성수 카페").region("서울").build();
  }

  private PopupAlertTargetRow row(Long userId, String token, Long popupId, String keyword) {
    return new TestRow(userId, token, popupId, keyword);
  }

  private record TestRow(Long userId, String token, Long popupId, String keyword)
      implements PopupAlertTargetRow {
    public Long getUserId() {
      return userId;
    }

    public String getUserUuid() {
      return "user-" + userId;
    }

    public String getFcmToken() {
      return token;
    }

    public Long getPopupId() {
      return popupId;
    }

    public String getKeyword() {
      return keyword;
    }
  }
}
