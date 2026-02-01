package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.domain.popup.application.PopupAdminService;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopPopupSubmissionResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[ADMIN]", description = "관리자 전용 API")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class PopupAdminController {

  private final PopupAdminService popupAdminService;

  @Operation(
      summary = "팝업 비활성화 (관리자 전용)",
      description =
          """
                          관리자만 사용할 수 있는 API입니다.

                          특정 팝업(popupUuid)을 비활성화(is_active = false) 상태로 변경합니다.

                          사용 조건:
                          • 요청한 userUuid가 ADMIN 권한이어야 함
                          • 존재하지 않는 userUuid 또는 popupUuid 요청 시 오류 반환

                          비활성화 처리 방식:
                          • Popup 엔티티의 activated 값을 false 로 변경 (dirty checking)
                          """)
  @PatchMapping("/user/{userUuid}/popup/{popupUuid}/deactivate")
  public ResponseEntity<Void> deactivatePopup(
      @PathVariable String userUuid, @PathVariable String popupUuid) {
    popupAdminService.deactivatePopup(userUuid, popupUuid);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "[V2] 팝업 비활성화 (관리자 전용)",
      description =
          """
                           권장 API 입니다. (JWT 기반 인증/인가)

                          - Authorization 헤더의 Bearer Access Token을 통해 인증합니다.
                          - ADMIN 권한이 있는 사용자만 접근 가능합니다.

                          - 처리 방식
                            - Popup 엔티티 activated 값을 false로 변경 (dirty checking)
                          """)
  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/popup/{popupUuid}/deactivate")
  public ResponseEntity<Void> deactivatePopupV2(@PathVariable String popupUuid) {
    popupAdminService.deactivatePopupV2(popupUuid);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "팝업스토어 제보 등록", description = "사용자가 팝업스토어 정보를 제보합니다.")
  @PostMapping("/popup-submissions")
  public ResponseEntity<Void> createPopupSubmission(
      @RequestBody PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto) {
    popupAdminService.createPopupSubmission(popupSubmissionCreateRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "팝업스토어 제보 목록 조회 (PENDING)",
      description = "관리자가 승인 대기(PENDING) 상태의 제보 목록을 조회합니다.")
  @GetMapping("popup-submissions")
  public ResponseEntity<List<PopPopupSubmissionResponseDto>> getPendingSubmissions() {
    List<PopPopupSubmissionResponseDto> popupSubmissionResponseDtoList =
        popupAdminService.getPendingSubmissions();

    return ResponseEntity.ok(popupSubmissionResponseDtoList);
  }

  @Operation(
      summary = "팝업스토어 제보 상태 변경",
      description = "제보 상태를 PENDING → APPROVED 또는 REJECTED 로 변경합니다.")
  @PatchMapping("/popup-submissions/{submissionId}/status")
  public ResponseEntity<Void> updateSubmissionStatus(
      @PathVariable Long submissionId,
      @RequestBody PopupSubmissionStatusUpdateRequestDto popupSubmissionStatusUpdateRequestDto) {
    popupAdminService.updateSubmissionStatus(submissionId, popupSubmissionStatusUpdateRequestDto);

    return ResponseEntity.ok().build();
  }
}
