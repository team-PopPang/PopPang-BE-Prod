package com.poppang.be.domain.popup.infrastructure.projection;

import java.time.LocalDate;

public interface PopupWebSearchRow {
  String getPopupUuid();

  String getPopupName();

  String getThumbnailUrl();

  String getRegion();

  LocalDate getStartDate();

  LocalDate getEndDate();
}
