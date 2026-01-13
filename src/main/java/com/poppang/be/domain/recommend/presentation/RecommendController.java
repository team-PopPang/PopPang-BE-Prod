package com.poppang.be.domain.recommend.presentation;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.recommend.application.RecommendService;
import com.poppang.be.domain.recommend.dto.response.RecommendFeaturedResponseDto;
import com.poppang.be.domain.recommend.dto.response.RecommendResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[RECOMMEND] 공통", description = "추천 관련 API")
@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
public class RecommendController {

  private final RecommendService recommendService;

  @Operation(summary = "추천(Recommend) 전체 조회", description = "전체 추천 카테고리 목록을 조회합니다.")
  @GetMapping
  public List<RecommendResponseDto> getAllRecommendList() {
    List<RecommendResponseDto> recommendResponseDtoList = recommendService.getAllRecommendList();

    return recommendResponseDtoList;
  }

  @Operation(
      summary = "지도 상단 Featured 추천 조회",
      description =
          """
        지도 화면 상단에 노출되는 Featured 추천 카테고리를 조회합니다.

        - 노출 대상 및 개수는 서버에서 관리됩니다.
        - 클라이언트는 별도의 조건 없이 호출합니다.
        - 지도 UI 상단 강조 영역에 사용됩니다.
        """)
  @GetMapping("/featured")
  public ResponseEntity<List<RecommendFeaturedResponseDto>> getFeaturedForMap() {
    List<RecommendFeaturedResponseDto> featuredForMap = recommendService.getFeaturedForMap();

    return ResponseEntity.ok(featuredForMap);
  }

  @Operation(summary = "[WEB] 추천(Recommend) 전체 조회", description = "[WEB] 전체 추천 카테고리 목록을 조회합니다.")
  @GetMapping("/web")
  public ApiResponse<List<RecommendResponseDto>> webGetAllRecommendList() {
    List<RecommendResponseDto> recommendResponseDtoList = recommendService.webGetAllRecommendList();

    return ApiResponse.ok(recommendResponseDtoList);
  }
}
