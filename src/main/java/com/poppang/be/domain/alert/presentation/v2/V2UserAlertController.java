package com.poppang.be.domain.alert.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.alert.application.V2UserAlertService;
import com.poppang.be.domain.alert.dto.v2.V2UserAlertDeleteRequestDto;
import com.poppang.be.domain.alert.dto.v2.V2UserAlertResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "[ALERT v2] 알림함", description = "JWT로 인증한 사용자의 알림함 API")
@RequestMapping(value = "/api/v2/user/alert", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2UserAlertController {

  private final V2UserAlertService alertService;

  @Operation(summary = "내 알림 팝업 목록 조회")
  @GetMapping("/popups")
  public ResponseEntity<ApiResponse<List<V2UserAlertResponseDto>>> getUserAlertPopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.ok(alertService.getUserAlertPopupList(requireUserUuid(principal))));
  }

  @Operation(summary = "내 알림 삭제")
  @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteUserAlert(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestBody(required = false) V2UserAlertDeleteRequestDto request) {
    alertService.deleteUserAlert(
        requireUserUuid(principal), request == null ? null : request.popupUuid());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "내 알림 읽음 처리")
  @PatchMapping("/read")
  public ResponseEntity<Void> readUserAlertPopup(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(required = false) String popupUuid) {
    alertService.readUserAlertPopup(requireUserUuid(principal), popupUuid);
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
