package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.domain.popup.application.PopupServiceImpl;
import com.poppang.be.domain.popup.dto.app.request.PopupRegisterRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupResponseDto;
import com.poppang.be.domain.popup.dto.app.response.RegionDistrictsResponse;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.enums.SortStandard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[POPUP] 공통", description = "팝업스토어 관련 API")
@RestController
@RequestMapping("/api/v1/popup")
@RequiredArgsConstructor
public class PopupController {

  private final PopupServiceImpl popupServiceImpl;

  @Operation(summary = "팝업 전체 조회", description = "모든 팝업스토어 정보를 조회합니다. (비활성화된 팝업 포함)")
  @GetMapping
  public ResponseEntity<List<PopupResponseDto>> getAllPopupList() {
    List<PopupResponseDto> allPopupList = popupServiceImpl.getAllPopupList();

    return ResponseEntity.ok(allPopupList);
  }

  @Operation(summary = "팝업 단건 조회", description = "popupUuid로 단건 팝업 조회합니다. ")
  @GetMapping("/{popupUuid}")
  public ResponseEntity<PopupResponseDto> getPopupByUuid(@PathVariable String popupUuid) {
    PopupResponseDto popupResponseDto = popupServiceImpl.getPopupByUuid(popupUuid);

    return ResponseEntity.ok(popupResponseDto);
  }

  @Operation(summary = "팝업 검색", description = "특정 키워드로 팝업스토어를 검색합니다.")
  @GetMapping("/search")
  public ResponseEntity<List<PopupResponseDto>> getSearchPopupList(@RequestParam("q") String q) {
    List<PopupResponseDto> searchPopupList = popupServiceImpl.getSearchPopupList(q);

    return ResponseEntity.ok(searchPopupList);
  }

  @Operation(
      summary = "다가오는 팝업 조회 (D-1 ~ D-10)",
      description = "오늘부터 10일 이내에 시작하는 팝업을 반환합니다.",
      tags = {"[POPUP] 공통"})
  @GetMapping("/upcoming")
  public ResponseEntity<List<PopupResponseDto>> getUpcomingPopupList(
      @Parameter(description = "며칠 뒤까지 조회 (기본 10)")
          @RequestParam(name = "upcomingDays", required = false)
          Integer upcomingDays) {
    List<PopupResponseDto> upcomingPopupList = popupServiceImpl.getUpcomingPopupList(upcomingDays);

    return ResponseEntity.ok(upcomingPopupList);
  }

  @Operation(
      summary = "유저별 추천 팝업 조회 API",
      description =
          """
                    사용자의 관심 키워드(UserRecommend)에 기반하여 추천 팝업 10개를 반환합니다.

                    추천 로직:
                    • 유저 키워드별로 최대 2개씩 추천 팝업을 수집
                    • 총 10개가 되지 않으면 활성 팝업 중에서 랜덤으로 채움
                    • start_date ≤ today ≤ end_date 조건의 ‘현재 운영 중’ 팝업만 추천 대상

                    참고:
                    • 유저가 설정한 추천 키워드(UserRecommend)가 없으면 → 전부 랜덤 추천
                    • 이미 추천된 팝업은 중복되지 않도록 제외 처리
                    """)
  @GetMapping("/{userUuid}/recommend")
  public ResponseEntity<List<PopupResponseDto>> getRecommendPopupList(
      @PathVariable String userUuid) {
    List<PopupResponseDto> recommendPopupList = popupServiceImpl.getRecommendPopupList(userUuid);

    return ResponseEntity.ok(recommendPopupList);
  }

  @Operation(
      summary = "진행 중인 팝업 조회",
      description =
          "현재 날짜 기준으로 오픈 중(진행 중)인 모든 팝업스토어 정보를 조회합니다. "
              + "시작일(`start_date`)이 오늘 이전이거나 같고, 종료일(`end_date`)이 오늘 이후이거나 같은 팝업만 반환됩니다.",
      tags = {"[POPUP] 공통"})
  @GetMapping("/inProgress")
  public ResponseEntity<List<PopupResponseDto>> getInProgressPopupList() {
    List<PopupResponseDto> inProgressPopupList = popupServiceImpl.getInProgressPopupList();

    return ResponseEntity.ok(inProgressPopupList);
  }

  @Tag(name = "[CRON]", description = "CRON 관련 API")
  @Operation(
      summary = "팝업 등록",
      description =
          "크롤링 또는 관리자가 신규 팝업스토어 데이터를 등록합니다. "
              + "이미지 리스트(`imageList`)와 추천 ID(`recommendIds`)를 함께 전달해야 합니다.")
  @PostMapping
  public ResponseEntity<Void> registerPopup(
      @RequestBody PopupRegisterRequestDto popupRegisterRequestDto) {
    popupServiceImpl.registerPopup(popupRegisterRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "지역/구 목록 조회",
      description =
          "DB의 popup.road_address를 분석하여 지역별 구 목록을 반환합니다. "
              + "서울은 '전체'와 실제 'OO구'들을 포함하고, 서울 외 지역은 '전체'만 포함합니다.")
  @GetMapping("/regions/districts")
  public ResponseEntity<List<RegionDistrictsResponse>> getRegionDistricts() {
    List<RegionDistrictsResponse> regionDistrictsResponseList =
        popupServiceImpl.getRegionDistricts();

    return ResponseEntity.ok(regionDistrictsResponseList);
  }

  @Operation(
      summary = "팝업 필터 조회 API",
      description =
          """
                    지역(region), 구(district), 정렬 기준(sortStandard), 좌표(latitude, longitude)에 따라 팝업 리스트를 필터링합니다.
                    - sortStandard: LIKES(좋아요 순), DISTANCE(가까운 순)
                    - district는 '전체'로 요청하면 전체 지역을 의미합니다.
                    - latitude, longitude는 가까운순 정렬 시 필수값입니다.
                    """,
      deprecated = true)
  @GetMapping("/filtered")
  public ResponseEntity<List<PopupResponseDto>> getFilteredPopupList(
      @RequestParam String region,
      @RequestParam(required = false) String district,
      @RequestParam(defaultValue = "LIKES") SortStandard sortStandard,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude) {
    List<PopupResponseDto> filteredPopupList =
        popupServiceImpl.getFilteredPopupList(region, district, sortStandard, latitude, longitude);

    return ResponseEntity.ok(filteredPopupList);
  }

  @Operation(
      summary = "[홈 뷰] 팝업 필터 조회",
      description =
          """
                    홈 화면에서 지역(region), 구(district), 정렬 기준(homeSortStandard)에 따라 팝업 리스트를 조회합니다.
                    - homeSortStandard:
                      • NEWEST : 최근 오픈 순
                      • CLOSING_SOON : 곧 종료될 순
                      • MOST_FAVORITED : 좋아요 많은 순
                      • MOST_VIEWED : 조회수 많은 순
                    - region, district는 선택된 지역과 구를 의미하며,
                      district가 '전체'일 경우 해당 지역 내 모든 팝업을 조회합니다.
                    - 반환되는 리스트는 진행 중(is_active = true, 날짜 유효)인 팝업만 포함합니다.
                    """)
  @GetMapping("/filtered/home")
  public ResponseEntity<List<PopupResponseDto>> getFilteredHomePopupList(
      @RequestParam String region,
      @RequestParam String district,
      @RequestParam HomeSortStandard homeSortStandard) {
    List<PopupResponseDto> filteredHomePopupList =
        popupServiceImpl.getFilteredHomePopupList(region, district, homeSortStandard);

    return ResponseEntity.ok(filteredHomePopupList);
  }

  @Operation(
      summary = "[지도뷰] 팝업 필터 조회 API",
      description =
          """
                    지역(region), 구(district), 정렬 기준(mapSortStandard), 좌표(latitude, longitude)에 따라 팝업 리스트를 필터링합니다.

                    • mapSortStandard:
                      - NEAREST(가까운 순)  ← 이 경우 latitude/longitude 필수
                      - MOST_FAVORITED(찜순)
                      - MOST_VIEWED(조회수순)
                      - NEWEST(최신순)
                      - CLOSING_SOON(마감임박순)

                    • district는 '전체'로 요청하면 전체 지역을 의미합니다.
                    • latitude, longitude는 가까운순 정렬 시 필수값입니다.
                    """)
  @GetMapping("/filtered/map")
  public ResponseEntity<List<PopupResponseDto>> getFilteredMapPopupList(
      @RequestParam String region,
      @RequestParam String district,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      @RequestParam MapSortStandard mapSortStandard) {
    List<PopupResponseDto> filteredMapPopupList =
        popupServiceImpl.getFilteredMapPopupList(
            region, district, latitude, longitude, mapSortStandard);

    return ResponseEntity.ok(filteredMapPopupList);
  }

  @Operation(
      summary = "연관 팝업 추천 조회",
      description =
          """
                특정 팝업과 동일한 추천 태그(recommend)를 기반으로
                최대 10개의 연관 팝업을 반환합니다.

                동작 방식:
                - 현재 팝업의 recommend 값 조회
                - 같은 recommend를 가진 활성 팝업에서 최대 10개 반환
                - 현재 팝업은 결과에서 제외
                - 부족할 경우 활성 팝업 중 랜덤으로 채움

                반환 조건:
                - is_active = true
                - start_date ≤ 오늘 ≤ end_date
                - 최대 10개
                - 중복 제거
                """)
  @GetMapping("/{popupUuid}/related")
  public ResponseEntity<List<PopupResponseDto>> getRelatedPopupList(
      @PathVariable String popupUuid) {
    List<PopupResponseDto> relatedPopupList = popupServiceImpl.getRelatedPopupList(popupUuid);

    return ResponseEntity.ok(relatedPopupList);
  }

  @Operation(
      summary = "랜덤 팝업 10개 조회",
      description =
          """
                활성 상태이며 현재 운영 중인 팝업 중에서
                랜덤하게 10개를 반환합니다.

                조건:
                • is_active = true
                • start_date ≤ 오늘 ≤ end_date

                참고:
                • 데이터가 10개 미만이면 가능한 만큼만 반환됩니다.
                • 동일 결과가 매 요청마다 달라질 수 있습니다.
                """)
  @GetMapping("/random")
  public ResponseEntity<List<PopupResponseDto>> getRandomPopupList() {
    List<PopupResponseDto> randomPopupList = popupServiceImpl.getRandomPopupList();

    return ResponseEntity.ok(randomPopupList);
  }
}
