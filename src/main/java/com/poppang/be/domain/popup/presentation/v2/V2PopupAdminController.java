package com.poppang.be.domain.popup.presentation.v2;

import com.poppang.be.domain.popup.application.V2PopupAdminService;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminListResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionStatusUpdateRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "[ADMIN v2]", description = "Access Token과 현재 ADMIN 권한이 필요한 관리자 API")
@RestController
@RequestMapping("/api/v2/admin")
@PreAuthorize("hasAuthority('TOKEN_ACCESS') and hasRole('ADMIN')")
@RequiredArgsConstructor
public class V2PopupAdminController {

  private final V2PopupAdminService popupAdminService;

  @Operation(summary = "팝업 비활성화")
  @PatchMapping("/popup/{popupUuid}/deactivate")
  public ResponseEntity<Void> deactivatePopup(@PathVariable String popupUuid) {
    popupAdminService.deactivatePopup(popupUuid);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "팝업 제보 목록 조회")
  @GetMapping(value = "/popup-submissions", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<V2PopupSubmissionAdminListResponseDto>> getPopupSubmissions(
      @RequestParam(defaultValue = "전체") String status) {
    return ResponseEntity.ok(popupAdminService.getPopupSubmissions(status));
  }

  @Operation(summary = "팝업 제보 상세 조회")
  @GetMapping(
      value = "/popup-submissions/{popupSubmissionId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<V2PopupSubmissionAdminDetailResponseDto> getPopupSubmissionDetail(
      @PathVariable Long popupSubmissionId) {
    return ResponseEntity.ok(popupAdminService.getPopupSubmissionDetail(popupSubmissionId));
  }

  @Operation(summary = "팝업 제보 승인 또는 반려")
  @PutMapping(
      value = "/popup-submissions/{popupSubmissionId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<V2PopupSubmissionAdminUpdateResponseDto> updatePopupSubmission(
      @PathVariable Long popupSubmissionId,
      @RequestPart(value = "request", required = false)
          V2PopupSubmissionAdminUpdateRequestDto request,
      @RequestPart(value = "images", required = false) List<MultipartFile> images) {
    return ResponseEntity.ok(
        popupAdminService.updatePopupSubmission(popupSubmissionId, request, images));
  }

  @Operation(summary = "팝업 제보 상태 변경")
  @PatchMapping("/popup-submissions/{submissionId}/status")
  public ResponseEntity<Void> updateSubmissionStatus(
      @PathVariable Long submissionId,
      @RequestBody V2PopupSubmissionStatusUpdateRequestDto request) {
    popupAdminService.updateSubmissionStatus(submissionId, request);
    return ResponseEntity.ok().build();
  }
}
