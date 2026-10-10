package com.poppang.be.domain.popup.infrastructure.projection;

public interface PopupAlertTargetRow {
  Long getUserId();

  String getUserUuid();

  String getFcmToken();

  Long getPopupId();

  String getKeyword();
}
