package com.poppang.be.domain.popup.application;

public interface PopupAdminService {

  void deactivatePopup(String userUuid, String popupUuid);

  void deactivatePopupV2(String popupUuid);
}
