package com.poppang.be.domain.recommend.presentation;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.recommend.application.RecommendServiceImpl;
import com.poppang.be.domain.recommend.dto.response.RecommendResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
public class RecommendController {

  private final RecommendServiceImpl recommendServiceImpl;

  @Operation(
      summary = "추천(Recommend) 전체 조회",
      description = "전체 추천 카테고리 목록을 조회합니다.",
      tags = {"[RECOMMEND] 공통"})
  @GetMapping
  public List<RecommendResponseDto> getAllRecommendList() {
    List<RecommendResponseDto> recommendResponseDtoList =
        recommendServiceImpl.getAllRecommendList();

    return recommendResponseDtoList;
  }

  @Operation(
      summary = "[WEB] 추천(Recommend) 전체 조회",
      description = "[WEB] 전체 추천 카테고리 목록을 조회합니다.",
      tags = {"[RECOMMEND] 공통"})
  @GetMapping("/web")
  public ApiResponse<List<RecommendResponseDto>> webGetAllRecommendList() {
    List<RecommendResponseDto> recommendResponseDtoList =
        recommendServiceImpl.webGetAllRecommendList();

    return ApiResponse.ok(recommendResponseDtoList);
  }
}
