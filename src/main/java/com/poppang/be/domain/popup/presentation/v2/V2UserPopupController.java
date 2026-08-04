package com.poppang.be.domain.popup.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.popup.application.V2UserPopupService;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
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
@Tag(name = "[POPUP-USER v2] 회원", description = "JWT로 인증한 사용자의 개인화 팝업 API")
@RequestMapping(value = "/api/v2/user/popups", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2UserPopupController {

  private final V2UserPopupService popupService;

  @Operation(summary = "팝업 전체 조회", description = "비활성화된 팝업을 포함한 모든 팝업을 조회합니다.")
  @GetMapping
  public ResponseEntity<List<V2UserPopupResponseDto>> getAllPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(popupService.getAllPopupList(requireAccessPrincipal(principal)));
  }

  @Operation(summary = "팝업 단건 조회")
  @GetMapping("/{popupUuid}")
  public ResponseEntity<V2UserPopupResponseDto> getPopupByUuid(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    return ResponseEntity.ok(
        popupService.getPopupByUuid(requireAccessPrincipal(principal), popupUuid));
  }

  @Operation(summary = "다가오는 팝업 조회 (D-1 ~ D-10)")
  @GetMapping("/upcoming")
  public ResponseEntity<List<V2UserPopupResponseDto>> getUpcomingPopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @Parameter(description = "며칠 뒤까지 조회 (기본 10)")
          @RequestParam(name = "upcomingDays", required = false)
          Integer upcomingDays) {
    return ResponseEntity.ok(
        popupService.getUpcomingPopupList(requireAccessPrincipal(principal), upcomingDays));
  }

  @Operation(summary = "팝업 검색")
  @GetMapping("/search")
  public ResponseEntity<List<V2UserPopupResponseDto>> getSearchPopupList(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestParam("q") String query) {
    return ResponseEntity.ok(
        popupService.getSearchPopupList(requireAccessPrincipal(principal), query));
  }

  @Operation(summary = "진행 중인 팝업 조회")
  @GetMapping("/inProgress")
  public ResponseEntity<List<V2UserPopupResponseDto>> getInProgressPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(
        popupService.getInProgressPopupList(requireAccessPrincipal(principal)));
  }

  @Operation(summary = "랜덤 팝업 10개 조회")
  @GetMapping("/random")
  public ResponseEntity<List<V2UserPopupResponseDto>> getRandomPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(popupService.getRandomPopupList(requireAccessPrincipal(principal)));
  }

  @Operation(summary = "팝업 무한 스크롤 목록 조회", description = "최신 팝업을 15개씩 커서 기반으로 조회합니다.")
  @GetMapping("/scroll")
  public ResponseEntity<V2UserPopupScrollResponseDto> getScrollPopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(name = "cursor", required = false) Long cursor) {
    return ResponseEntity.ok(
        popupService.getScrollPopupList(requireAccessPrincipal(principal), cursor));
  }

  @Operation(summary = "[홈 뷰] 팝업 필터 조회")
  @GetMapping("/filtered/home")
  public ResponseEntity<List<V2UserPopupResponseDto>> getFilteredHomePopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam String region,
      @RequestParam String district,
      @RequestParam HomeSortStandard homeSortStandard) {
    return ResponseEntity.ok(
        popupService.getFilteredHomePopupList(
            requireAccessPrincipal(principal), region, district, homeSortStandard));
  }

  @Operation(summary = "[지도 뷰] 팝업 필터 조회")
  @GetMapping("/filtered/map")
  public ResponseEntity<List<V2UserPopupResponseDto>> getFilteredMapPopupList(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam String region,
      @RequestParam String district,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      @RequestParam MapSortStandard mapSortStandard) {
    return ResponseEntity.ok(
        popupService.getFilteredMapPopupList(
            requireAccessPrincipal(principal),
            region,
            district,
            latitude,
            longitude,
            mapSortStandard));
  }

  @Operation(summary = "사용자 관심사 기반 팝업 추천 조회")
  @GetMapping("/recommend")
  public ResponseEntity<List<V2UserPopupResponseDto>> getRecommendPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(popupService.getRecommendPopupList(requireAccessPrincipal(principal)));
  }

  @Operation(summary = "사용자별 연관 팝업 조회")
  @GetMapping("/{popupUuid}/related")
  public ResponseEntity<List<V2UserPopupResponseDto>> getRelatedPopupList(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    return ResponseEntity.ok(
        popupService.getRelatedPopupList(requireAccessPrincipal(principal), popupUuid));
  }

  @Operation(summary = "추천 카테고리별 사용자 팝업 조회")
  @GetMapping("/recommendations/{recommendId}")
  public ResponseEntity<List<V2UserPopupResponseDto>> getRecommendationPopupList(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long recommendId) {
    return ResponseEntity.ok(
        popupService.getRecommendationPopupList(requireAccessPrincipal(principal), recommendId));
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
