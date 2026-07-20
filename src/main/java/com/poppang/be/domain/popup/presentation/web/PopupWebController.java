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
import io.swagger.v3.oas.annotations.media.Schema;
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
      operationId = "getWebInProgressPopupList",
      summary = "[WEB] 현재 진행 중인 팝업 목록 조회",
      description =
          """
          현재 날짜를 기준으로 진행 중(is_active = true, 시작일 ≤ 오늘 ≤ 종료일)인 팝업만 반환합니다.
          지역과 구를 선택적으로 필터링할 수 있으며, district를 생략하거나 '전체'로 지정하면 해당 지역 전체를 조회합니다.
          정렬 기준은 MOST_FAVORITED(찜 많은 순), MOST_VIEWED(조회수 많은 순), NEWEST(최근 오픈 순), CLOSING_SOON(마감 임박 순)입니다.
          필터를 사용하면서 sort를 생략하면 CLOSING_SOON을 적용하고, 모든 parameter를 생략하면 기존 목록 정렬을 유지합니다.
          """)
  @GetMapping("/in-progress")
  public ApiResponse<List<PopupWebInProgressResponseDto>> getWebInProgressPopupList(
      @Parameter(description = "지역. 예: 서울", example = "서울")
          @RequestParam(name = "region", required = false)
          String region,
      @Parameter(description = "구. 생략하거나 '전체'이면 해당 지역 전체", example = "성동구")
          @RequestParam(name = "district", required = false)
          String district,
      @Parameter(
              description = "정렬 기준",
              schema =
                  @Schema(
                      allowableValues = {
                        "MOST_FAVORITED",
                        "MOST_VIEWED",
                        "NEWEST",
                        "CLOSING_SOON"
                      }))
          @RequestParam(name = "sort", required = false)
          String sort) {
    List<PopupWebInProgressResponseDto> inProgressPopupList =
        popupWebService.getInProgressPopupList(region, district, sort);

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
