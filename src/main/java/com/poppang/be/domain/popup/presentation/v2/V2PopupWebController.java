package com.poppang.be.domain.popup.presentation.v2;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.popup.application.V2PopupWebService;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebSearchResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebUpcomingResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[V2] [WEB] [POPUP]", description = "v2 공개 Web 팝업 API")
@RestController
@RequestMapping(value = "/api/v2/web/popup", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class V2PopupWebController {

  private final V2PopupWebService popupWebService;

  @Operation(summary = "[V2] [WEB] 랜덤 팝업 목록 조회")
  @GetMapping("/random")
  public ApiResponse<List<V2PopupWebRandomResponseDto>> getRandomPopupList() {
    return ApiResponse.ok(popupWebService.getRandomPopupList());
  }

  @Operation(summary = "[V2] [WEB] 인기 팝업 목록 조회")
  @GetMapping("/favorite")
  public ApiResponse<List<V2PopupWebFavoriteResponseDto>> getFavoritePopupList() {
    return ApiResponse.ok(popupWebService.getFavoritePopupList());
  }

  @Operation(summary = "[V2] [WEB] 진행 중 팝업 목록 조회")
  @GetMapping("/in-progress")
  public ApiResponse<List<V2PopupWebInProgressResponseDto>> getInProgressPopupList(
      @RequestParam(name = "region", required = false) String region,
      @RequestParam(name = "district", required = false) String district,
      @RequestParam(name = "sort", required = false) String sort) {
    return ApiResponse.ok(popupWebService.getInProgressPopupList(region, district, sort));
  }

  @Operation(summary = "[V2] [WEB] 오픈 예정 팝업 목록 조회")
  @GetMapping("/upcoming")
  public ApiResponse<List<V2PopupWebUpcomingResponseDto>> getUpcomingPopupList() {
    return ApiResponse.ok(popupWebService.getUpcomingPopupList());
  }

  @Operation(summary = "[V2] [WEB] 팝업 검색")
  @GetMapping("/search")
  public ApiResponse<List<V2PopupWebSearchResponseDto>> getSearchPopupList(
      @RequestParam(name = "q", required = false) String query) {
    return ApiResponse.ok(popupWebService.getSearchPopupList(query));
  }

  @Operation(summary = "[V2] [WEB] 팝업 상세 조회")
  @GetMapping("/{popupUuid}")
  public ApiResponse<V2PopupWebDetailResponseDto> getPopupDetail(@PathVariable String popupUuid) {
    return ApiResponse.ok(popupWebService.getPopupDetail(popupUuid));
  }
}
