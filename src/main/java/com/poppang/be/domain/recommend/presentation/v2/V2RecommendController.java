package com.poppang.be.domain.recommend.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.recommend.application.V2RecommendService;
import com.poppang.be.domain.recommend.dto.v2.V2RecommendFeaturedResponseDto;
import com.poppang.be.domain.recommend.dto.v2.V2RecommendResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "[RECOMMEND v2] 앱", description = "JWT로 인증한 사용자의 추천 카테고리 API")
@RequestMapping(value = "/api/v2/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2RecommendController {

  private final V2RecommendService recommendService;

  @Operation(summary = "추천 카테고리 전체 조회")
  @GetMapping
  public ResponseEntity<List<V2RecommendResponseDto>> getAllRecommendList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(recommendService.getAllRecommendList());
  }

  @Operation(summary = "지도 상단 Featured 추천 조회")
  @GetMapping("/featured")
  public ResponseEntity<List<V2RecommendFeaturedResponseDto>> getFeaturedForMap(
      @AuthenticationPrincipal JwtPrincipal principal) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(recommendService.getFeaturedForMap());
  }

  private void requireAccessPrincipal(JwtPrincipal principal) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.ACCESS
        || principal.userUuid() == null
        || principal.userUuid().isBlank()) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
  }
}
