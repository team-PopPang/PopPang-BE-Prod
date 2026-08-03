package com.poppang.be.domain.popup.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.popup.application.V2PopupTotalViewCountService;
import com.poppang.be.domain.popup.dto.v2.V2PopupTotalViewCountResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "[POPUP v2] 조회수", description = "JWT로 인증한 사용자의 팝업 조회수 API")
@RequestMapping(value = "/api/v2/popup", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2PopupTotalViewController {

  private final V2PopupTotalViewCountService popupTotalViewCountService;

  @Operation(summary = "팝업 상세 진입 시 조회수 증가")
  @PostMapping("/{popupUuid}/view")
  public ResponseEntity<Void> increment(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    requireAccessPrincipal(principal);
    popupTotalViewCountService.increment(popupUuid);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "팝업 총 조회수 조회")
  @GetMapping("/{popupUuid}/total-view-count")
  public ResponseEntity<V2PopupTotalViewCountResponseDto> getTotalViewCount(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(popupTotalViewCountService.getTotalViewCount(popupUuid));
  }

  @Operation(summary = "팝업 Redis 조회수 증가분 조회")
  @GetMapping("/{popupUuid}/view-count")
  public ResponseEntity<Map<String, Long>> getViewCount(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    requireAccessPrincipal(principal);
    return ResponseEntity.ok(Map.of("viewCount", popupTotalViewCountService.getDelta(popupUuid)));
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
