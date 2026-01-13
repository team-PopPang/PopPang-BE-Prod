package com.poppang.be.domain.alert.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.dto.request.UserAlertDeleteRequestDto;
import com.poppang.be.domain.alert.dto.request.UserAlertRegisterRequestDto;
import com.poppang.be.domain.alert.dto.response.UserAlertResponseDto;
import com.poppang.be.domain.alert.entity.UserAlert;
import com.poppang.be.domain.alert.infrastructure.UserAlertRepository;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.app.response.PopupUserResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.mapper.PopupUserResponseDtoMapper;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAlertServiceImpl implements UserAlertService {

  private final UserAlertRepository userAlertRepository;
  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupUserResponseDtoMapper popupUserResponseDtoMapper;

  @Override
  @Transactional
  public void registerUserAlert(
      String userUuid, UserAlertRegisterRequestDto userAlertRegisterRequestDto) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Popup popup =
        popupRepository
            .findByUuid(userAlertRegisterRequestDto.getPopupUuid())
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    if (userAlertRepository.existsByUser_IdAndPopup_Id(user.getId(), popup.getId())) {
      throw new BaseException(ErrorCode.USER_ALERT_ALREADY_EXISTS);
    }

    UserAlert userAlert =
        UserAlert.builder()
            .user(user)
            .popup(popup)
            .alertedAt(LocalDateTime.now())
            .readAt(null)
            .build();

    userAlertRepository.save(userAlert);
  }

  @Override
  @Transactional
  public void deleteUserAlert(
      String userUuid, UserAlertDeleteRequestDto userAlertDeleteRequestDto) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Popup popup =
        popupRepository
            .findByUuid(userAlertDeleteRequestDto.getPopupUuid())
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    UserAlert userAlert =
        userAlertRepository
            .findByUser_IdAndPopup_Id(user.getId(), popup.getId())
            .orElseThrow(() -> new BaseException(ErrorCode.USER_ALERT_NOT_FOUND));

    userAlertRepository.delete(userAlert);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserAlertResponseDto> getUserAlertPopupList(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<UserAlert> userAlertList =
        userAlertRepository.findAllByUserIdOrderByAlertedAtDesc(user.getId());

    if (userAlertList.isEmpty()) {
      return List.of();
    }

    List<Popup> popupList = userAlertList.stream().map(UserAlert::getPopup).toList();

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(uf -> uf.getPopup().getId())
            .collect(Collectors.toSet());

    List<PopupUserResponseDto> popupUserResponseDtoList =
        popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);

    List<UserAlertResponseDto> result = new ArrayList<>(popupUserResponseDtoList.size());

    for (int i = 0; i < popupUserResponseDtoList.size(); i++) {
      PopupUserResponseDto popupDto = popupUserResponseDtoList.get(i);
      UserAlert userAlert = userAlertList.get(i); // 같은 순서라고 가정

      boolean isRead = (userAlert.getReadAt() != null);

      UserAlertResponseDto alertDto = UserAlertResponseDto.from(popupDto, isRead);
      result.add(alertDto);
    }

    return result;
  }

  @Override
  @Transactional
  public void readUserAlertPopup(String userUuid, String popupUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    UserAlert userAlert =
        userAlertRepository
            .findByUser_IdAndPopup_Id(user.getId(), popup.getId())
            .orElseThrow(() -> new BaseException(ErrorCode.USER_ALERT_NOT_FOUND));

    if (userAlert.getReadAt() == null) {
      userAlert.markAsRead();
    }
  }
}
