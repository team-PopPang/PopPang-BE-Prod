package com.poppang.be.domain.popup.infrastructure.projection;

import java.time.LocalDate;

public interface PopupWebFavoriteRow {
    String getPopupUuid();
    String getPopupName();
    String getThumbnailUrl();
    String getRegion();
    LocalDate getStartDate();
    LocalDate getEndDate();
}
