package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.V2PopupTotalViewCountResponseDto;

public interface V2PopupTotalViewCountService {

  long increment(String popupUuid);

  long getDelta(String popupUuid);

  V2PopupTotalViewCountResponseDto getTotalViewCount(String popupUuid);
}
