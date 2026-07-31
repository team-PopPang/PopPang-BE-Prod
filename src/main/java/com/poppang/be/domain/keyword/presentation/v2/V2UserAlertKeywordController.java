package com.poppang.be.domain.keyword.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.keyword.application.V2UserAlertKeywordService;
import com.poppang.be.domain.keyword.dto.v2.V2AlertKeywordRequestDto;
import com.poppang.be.domain.keyword.dto.v2.V2AlertKeywordResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "[ALERT KEYWORD v2] 알림 키워드", description = "JWT로 인증한 사용자의 알림 키워드 API")
@RequestMapping(value = "/api/v2/alert-keyword", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2UserAlertKeywordController {

  private final V2UserAlertKeywordService keywordService;

  @Operation(summary = "내 알림 키워드 목록 조회")
  @GetMapping
  public ResponseEntity<ApiResponse<List<V2AlertKeywordResponseDto>>> getUserAlertKeywords(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.ok(keywordService.getUserAlertKeywords(requireUserUuid(principal))));
  }

  @Operation(summary = "내 알림 키워드 등록")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> registerAlertKeyword(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestBody V2AlertKeywordRequestDto request) {
    keywordService.registerAlertKeyword(requireUserUuid(principal), request.keyword());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "내 알림 키워드 삭제")
  @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteAlertKeyword(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestBody V2AlertKeywordRequestDto request) {
    keywordService.deleteAlertKeyword(requireUserUuid(principal), request.keyword());
    return ResponseEntity.ok().build();
  }

  private String requireUserUuid(JwtPrincipal principal) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.ACCESS
        || principal.userUuid() == null
        || principal.userUuid().isBlank()) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return principal.userUuid();
  }
}
