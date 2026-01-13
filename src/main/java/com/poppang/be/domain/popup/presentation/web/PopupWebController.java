package com.poppang.be.domain.popup.presentation.web;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.popup.application.PopupWebService;
import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[WEB] [POPUP]", description = "팝업스토어 관련 API")
@RestController
@RequestMapping("/api/v1/web/popup")
@RequiredArgsConstructor
public class PopupWebController {

  private final PopupWebService popupWebService;

  @Operation(summary = "[WEB] 랜덤 팝업 목록 조회", description = "웹 메인 화면에 노출되는 랜덤 팝업스토어 목록을 조회합니다.")
  @GetMapping("/random")
  public ApiResponse<List<PopupWebRandomResponseDto>> getRandomPopupList() {
    List<PopupWebRandomResponseDto> randomPopupList = popupWebService.getRandomPopupList();

    return ApiResponse.ok(randomPopupList);
  }

  @Operation(
      summary = "[WEB] 인기(즐겨찾기) 팝업 목록 조회",
      description = "즐겨찾기 수 기준으로 정렬된 인기 팝업스토어 목록을 조회합니다.")
  @GetMapping("/favorite")
  public ApiResponse<List<PopupWebFavoriteResponseDto>> getFavoritePopupList() {
    List<PopupWebFavoriteResponseDto> favoritePopupList = popupWebService.getFavoritePopupList();

    return ApiResponse.ok(favoritePopupList);
  }

  @Operation(summary = "[WEB] 오픈 예정 팝업 목록 조회", description = "아직 시작되지 않은 오픈 예정 팝업스토어 목록을 조회합니다.")
  @GetMapping("/upcoming")
  public ApiResponse<List<PopupWebUpcomingResponseDto>> getUpcomingPopupList() {
    List<PopupWebUpcomingResponseDto> upcomingPopupList = popupWebService.getUpcomingPopupList();

    return ApiResponse.ok(upcomingPopupList);
  }
}
