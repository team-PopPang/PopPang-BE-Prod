package com.poppang.be.domain.popup.presentation.web;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.popup.application.PopupWebService;
import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "[WEB] [POPUP]", description = "[WEB] 팝업스토어 관련 API")
@RestController
@RequestMapping("/api/v1/web/popup")
@RequiredArgsConstructor
public class PopupWebController {

    private final PopupWebService popupWebService;

    @GetMapping("/random")
    public ApiResponse<List<PopupWebRandomResponseDto>> getRandomPopupList() {
        List<PopupWebRandomResponseDto> randomPopupList = popupWebService.getRandomPopupList();

        return ApiResponse.ok(randomPopupList);
    }

    @GetMapping("/favorite")
    public ApiResponse<List<PopupWebFavoriteResponseDto>> getFavoritePopupList() {
        List<PopupWebFavoriteResponseDto> favoritePopupList = popupWebService.getFavoritePopupList();

        return ApiResponse.ok(favoritePopupList);
    }

    @GetMapping("/upcoming")
    public ApiResponse<List<PopupWebUpcomingResponseDto>> getUpcomingPopupList() {
        List<PopupWebUpcomingResponseDto> upcomingPopupList = popupWebService.getUpcomingPopupList();

        return ApiResponse.ok(upcomingPopupList);
    }

}
