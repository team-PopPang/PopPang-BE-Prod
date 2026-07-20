package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.web.response.PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface PopupWebService {
  List<PopupWebRandomResponseDto> getRandomPopupList();

  List<PopupWebFavoriteResponseDto> getFavoritePopupList();

  List<PopupWebInProgressResponseDto> getInProgressPopupList();

  List<PopupWebInProgressResponseDto> getInProgressPopupList(
      String region, String district, String sort);

  List<PopupWebSearchResponseDto> getSearchPopupList(String q);

  List<PopupWebUpcomingResponseDto> getUpcomingPopupList();

  PopupWebDetailResponseDto getPopupDetail(String popupUuid);
}
