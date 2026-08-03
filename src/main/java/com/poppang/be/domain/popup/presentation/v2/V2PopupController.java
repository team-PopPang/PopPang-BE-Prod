package com.poppang.be.domain.popup.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.popup.application.V2PopupService;
import com.poppang.be.domain.popup.dto.v2.V2PopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2RegionDistrictsResponseDto;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.enums.SortStandard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "[POPUP v2] 일반 조회", description = "JWT로 인증한 사용자의 일반 팝업 조회 API")
@RequestMapping(value = "/api/v2/popup", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2PopupController {

  private final V2PopupService popupService;

  @Operation(summary = "팝업 전체 조회", description = "비활성화된 팝업을 포함한 모든 팝업을 조회합니다.")
  @GetMapping
  public ResponseEntity<List<V2PopupResponseDto>> getAllPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getAllPopupList());
  }

  @Operation(summary = "팝업 단건 조회")
  @GetMapping("/{popupUuid}")
  public ResponseEntity<V2PopupResponseDto> getPopupByUuid(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getPopupByUuid(popupUuid));
  }

  @Operation(summary = "팝업 검색")
  @GetMapping("/search")
  public ResponseEntity<List<V2PopupResponseDto>> getSearchPopupList(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestParam("q") String query) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getSearchPopupList(query));
  }

  @Operation(summary = "다가오는 팝업 조회 (D-1 ~ D-10)")
  @GetMapping("/upcoming")
  public ResponseEntity<List<V2PopupResponseDto>> getUpcomingPopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @Parameter(description = "며칠 뒤까지 조회 (기본 10)")
          @RequestParam(name = "upcomingDays", required = false)
          Integer upcomingDays) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getUpcomingPopupList(upcomingDays));
  }

  @Operation(summary = "진행 중인 팝업 조회")
  @GetMapping("/inProgress")
  public ResponseEntity<List<V2PopupResponseDto>> getInProgressPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getInProgressPopupList());
  }

  @Operation(summary = "지역/구 목록 조회")
  @GetMapping("/regions/districts")
  public ResponseEntity<List<V2RegionDistrictsResponseDto>> getRegionDistricts(
      @AuthenticationPrincipal JwtPrincipal principal) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getRegionDistricts());
  }

  @Operation(summary = "랜덤 팝업 10개 조회")
  @GetMapping("/random")
  public ResponseEntity<List<V2PopupResponseDto>> getRandomPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getRandomPopupList());
  }

  @Operation(summary = "팝업 필터 조회")
  @GetMapping("/filtered")
  public ResponseEntity<List<V2PopupResponseDto>> getFilteredPopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam String region,
      @RequestParam(required = false) String district,
      @RequestParam(defaultValue = "LIKES") SortStandard sortStandard,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(
        popupService.getFilteredPopupList(region, district, sortStandard, latitude, longitude));
  }

  @Operation(summary = "[홈 뷰] 팝업 필터 조회")
  @GetMapping("/filtered/home")
  public ResponseEntity<List<V2PopupResponseDto>> getFilteredHomePopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam String region,
      @RequestParam String district,
      @RequestParam HomeSortStandard homeSortStandard) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(
        popupService.getFilteredHomePopupList(region, district, homeSortStandard));
  }

  @Operation(summary = "[지도 뷰] 팝업 필터 조회")
  @GetMapping("/filtered/map")
  public ResponseEntity<List<V2PopupResponseDto>> getFilteredMapPopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam String region,
      @RequestParam String district,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      @RequestParam MapSortStandard mapSortStandard) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(
        popupService.getFilteredMapPopupList(
            region, district, latitude, longitude, mapSortStandard));
  }

  @Operation(summary = "연관 팝업 추천 조회")
  @GetMapping("/{popupUuid}/related")
  public ResponseEntity<List<V2PopupResponseDto>> getRelatedPopupList(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getRelatedPopupList(popupUuid));
  }

  @Operation(summary = "추천 카테고리별 팝업 목록 조회")
  @GetMapping("/recommendations/{recommendId}")
  public ResponseEntity<List<V2PopupResponseDto>> getRecommendationPopupList(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long recommendId) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupService.getRecommendationPopupList(recommendId));
  }

  @Operation(summary = "유저별 추천 팝업 조회")
  @GetMapping("/recommend")
  public ResponseEntity<List<V2PopupResponseDto>> getRecommendPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(popupService.getRecommendPopupList(requireAccessPrincipal(principal)));
  }

  private String requireAccessPrincipal(JwtPrincipal principal) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.ACCESS
        || principal.userUuid() == null
        || principal.userUuid().isBlank()) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return principal.userUuid();
  }
}
