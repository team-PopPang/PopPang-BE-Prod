package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface PopupWebService {
    List<PopupWebRandomResponseDto> getRandomPopupList();

    List<PopupWebFavoriteResponseDto> getFavoritePopupList();

    List<PopupWebUpcomingResponseDto> getUpcomingPopupList();
}
