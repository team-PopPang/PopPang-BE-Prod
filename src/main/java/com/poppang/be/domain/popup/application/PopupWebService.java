package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.web.response.PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface PopupWebService {
  List<PopupWebRandomResponseDto> getRandomPopupList();

  List<PopupWebFavoriteResponseDto> getFavoritePopupList();

  List<PopupWebUpcomingResponseDto> getUpcomingPopupList();

  PopupWebDetailResponseDto getPopupDetail(String popupUuid);
}
