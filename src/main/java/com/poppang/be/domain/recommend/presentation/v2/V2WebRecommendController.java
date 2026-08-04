package com.poppang.be.domain.recommend.presentation.v2;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.recommend.application.V2WebRecommendService;
import com.poppang.be.domain.recommend.dto.v2.V2WebRecommendResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[V2] [WEB] [RECOMMEND]", description = "v2 공개 Web 추천 API")
@RestController
@RequestMapping(value = "/api/v2/web/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class V2WebRecommendController {

  private final V2WebRecommendService recommendService;

  @Operation(summary = "[V2] [WEB] 추천 전체 조회")
  @GetMapping
  public ApiResponse<List<V2WebRecommendResponseDto>> getAllRecommendList() {
    return ApiResponse.ok(recommendService.getAllRecommendList());
  }
}
