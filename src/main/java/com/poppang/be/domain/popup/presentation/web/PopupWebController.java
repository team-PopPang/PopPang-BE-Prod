package com.poppang.be.domain.popup.presentation.web;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.popup.application.PopupWebService;
import com.poppang.be.domain.popup.dto.web.response.PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[WEB] [POPUP]", description = "팝업스토어 관련 API")
@RestController
@RequestMapping(value = "/api/v1/web/popup", produces = MediaType.APPLICATION_JSON_VALUE)
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

  @Operation(
      summary = "[WEB] 현재 진행 중인 팝업 목록 조회",
      description = "현재 날짜를 기준으로 운영 중인 팝업스토어 목록을 조회합니다.")
  @GetMapping("/in-progress")
  public ApiResponse<List<PopupWebInProgressResponseDto>> getWebInProgressPopupList() {
    List<PopupWebInProgressResponseDto> inProgressPopupList =
        popupWebService.getInProgressPopupList();

    return ApiResponse.ok(inProgressPopupList);
  }

  @Operation(summary = "[WEB] 오픈 예정 팝업 목록 조회", description = "아직 시작되지 않은 오픈 예정 팝업스토어 목록을 조회합니다.")
  @GetMapping("/upcoming")
  public ApiResponse<List<PopupWebUpcomingResponseDto>> getUpcomingPopupList() {
    List<PopupWebUpcomingResponseDto> upcomingPopupList = popupWebService.getUpcomingPopupList();

    return ApiResponse.ok(upcomingPopupList);
  }

  @Operation(
      operationId = "getWebSearchPopupList",
      summary = "[WEB] 팝업 검색",
      description = "검색어를 이용해 웹에 공개된 팝업스토어 목록을 검색합니다.")
  @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<List<PopupWebSearchResponseDto>> getWebSearchPopupList(
      @Parameter(description = "검색어", required = true) @RequestParam(name = "q", required = false)
          String q) {
    List<PopupWebSearchResponseDto> searchPopupList = popupWebService.getSearchPopupList(q);

    return ApiResponse.ok(searchPopupList);
  }

  @Operation(summary = "팝업스토어 상세 조회", description = "popupUuid를 이용해 팝업스토어의 상세 정보를 조회합니다.")
  @GetMapping("/{popupUuid}")
  public ApiResponse<PopupWebDetailResponseDto> getPopupDetail(@PathVariable String popupUuid) {
    PopupWebDetailResponseDto popupDetail = popupWebService.getPopupDetail(popupUuid);

    return ApiResponse.ok(popupDetail);
  }

}
