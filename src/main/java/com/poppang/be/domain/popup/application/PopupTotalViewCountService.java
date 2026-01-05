package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.response.PopupTotalViewCountResponseDto;

public interface PopupTotalViewCountService {

  long increment(String popupId);

  long getDelta(String popupUuid);

  PopupTotalViewCountResponseDto getTotalViewCount(String popupUuid);
}
