package com.poppang.be.domain.alert.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.dto.v2.V2WorkerUserAlertRegisterRequestDto;
import com.poppang.be.domain.alert.entity.UserAlert;
import com.poppang.be.domain.alert.infrastructure.UserAlertRepository;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2InternalUserAlertServiceImpl implements V2InternalUserAlertService {

  private final UserAlertRepository userAlertRepository;
  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;

  @Override
  @Transactional
  public void registerUserAlert(String userUuid, V2WorkerUserAlertRegisterRequestDto request) {
    if (userUuid == null
        || userUuid.isBlank()
        || request == null
        || request.popupUuid() == null
        || request.popupUuid().isBlank()) {
      throw new BaseException(ErrorCode.INVALID_WORKER_ALERT_REQUEST);
    }
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    Popup popup =
        popupRepository
            .findByUuid(request.popupUuid())
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    if (userAlertRepository.existsByUser_IdAndPopup_Id(user.getId(), popup.getId())) {
      throw new BaseException(ErrorCode.USER_ALERT_ALREADY_EXISTS);
    }

    userAlertRepository.save(
        UserAlert.builder()
            .user(user)
            .popup(popup)
            .alertedAt(LocalDateTime.now())
            .readAt(null)
            .build());
  }
}
