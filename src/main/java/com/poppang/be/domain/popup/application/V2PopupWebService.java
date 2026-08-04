package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebSearchResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebUpcomingResponseDto;
import java.util.List;

public interface V2PopupWebService {

  List<V2PopupWebRandomResponseDto> getRandomPopupList();

  List<V2PopupWebFavoriteResponseDto> getFavoritePopupList();

  List<V2PopupWebInProgressResponseDto> getInProgressPopupList(
      String region, String district, String sort);

  List<V2PopupWebUpcomingResponseDto> getUpcomingPopupList();

  List<V2PopupWebSearchResponseDto> getSearchPopupList(String query);

  V2PopupWebDetailResponseDto getPopupDetail(String popupUuid);
}
