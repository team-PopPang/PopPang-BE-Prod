package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupAdminServiceImpl implements PopupAdminService {

  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;

  @Override
  @Transactional
  public void deactivatePopup(String userUuid, String popupUuid) {

    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    if (user.getRole() != Role.ADMIN) {
      throw new BaseException(ErrorCode.ACCESS_DENIED);
    }

    popup.deactivate();
  }

  @Override
  @Transactional
  public void deactivatePopupV2(String popupUuid) {
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    popup.deactivate();
  }
}
