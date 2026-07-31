package com.poppang.be.domain.alert.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.dto.v2.V2UserAlertResponseDto;
import com.poppang.be.domain.alert.entity.UserAlert;
import com.poppang.be.domain.alert.infrastructure.UserAlertRepository;
import com.poppang.be.domain.favorite.application.V2FavoritePopupResponseDtoMapper;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2UserAlertServiceImpl implements V2UserAlertService {

  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;
  private final UserAlertRepository userAlertRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final V2FavoritePopupResponseDtoMapper popupResponseMapper;

  @Override
  @Transactional(readOnly = true)
  public List<V2UserAlertResponseDto> getUserAlertPopupList(String userUuid) {
    Users user = findUser(userUuid);
    List<UserAlert> alerts = userAlertRepository.findAllByUserIdOrderByAlertedAtDesc(user.getId());
    if (alerts.isEmpty()) {
      return List.of();
    }

    List<Popup> popups = alerts.stream().map(UserAlert::getPopup).toList();
    Set<Long> favoritePopupIds =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(favorite -> favorite.getPopup().getId())
            .collect(Collectors.toSet());
    List<V2FavoritePopupResponseDto> popupResponses =
        popupResponseMapper.toResponseList(popups, favoritePopupIds);

    List<V2UserAlertResponseDto> responses = new ArrayList<>(popupResponses.size());
    for (int index = 0; index < popupResponses.size(); index++) {
      responses.add(
          V2UserAlertResponseDto.from(
              popupResponses.get(index), alerts.get(index).getReadAt() != null));
    }
    return responses;
  }

  @Override
  @Transactional
  public void deleteUserAlert(String userUuid, String popupUuid) {
    requirePopupUuid(popupUuid);
    Users user = findUser(userUuid);
    Popup popup = findPopup(popupUuid);
    UserAlert alert = findAlert(user, popup);
    userAlertRepository.delete(alert);
  }

  @Override
  @Transactional
  public void readUserAlertPopup(String userUuid, String popupUuid) {
    requirePopupUuid(popupUuid);
    Users user = findUser(userUuid);
    Popup popup = findPopup(popupUuid);
    UserAlert alert = findAlert(user, popup);
    if (alert.getReadAt() == null) {
      alert.markAsRead();
    }
  }

  private Users findUser(String userUuid) {
    return usersRepository
        .findByUuid(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
  }

  private Popup findPopup(String popupUuid) {
    return popupRepository
        .findByUuid(popupUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));
  }

  private UserAlert findAlert(Users user, Popup popup) {
    return userAlertRepository
        .findByUser_IdAndPopup_Id(user.getId(), popup.getId())
        .orElseThrow(() -> new BaseException(ErrorCode.USER_ALERT_NOT_FOUND));
  }

  private void requirePopupUuid(String popupUuid) {
    if (popupUuid == null || popupUuid.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_USER_REQUEST);
    }
  }
}
