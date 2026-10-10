package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.popup.application.PopupAlertTargetService;
import com.poppang.be.domain.popup.dto.app.request.PopupAlertTargetRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupAlertTargetResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/popup/alert-targets")
@RequiredArgsConstructor
@Tag(name = "[CRON]", description = "팝업 수집기 API")
@PreAuthorize("hasAuthority('SERVICE_WORKER')")
public class PopupAlertTargetController {
  private final PopupAlertTargetService service;

  @Operation(
      summary = "팝업 알림 대상 조회 및 기록",
      description = "키워드가 일치하는 사용자에게 알림 기록을 생성하고 FCM 토큰이 있는 대상을 반환합니다. 실제 푸시 발송은 수집기가 담당합니다.")
  @SecurityRequirement(name = "workerApiKeyAuth")
  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<List<PopupAlertTargetResponseDto>> findTargets(
      @RequestBody PopupAlertTargetRequestDto request) {
    return ApiResponse.ok(service.findTargets(request));
  }
}
