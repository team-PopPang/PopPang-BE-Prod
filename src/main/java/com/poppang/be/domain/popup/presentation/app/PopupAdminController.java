package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.domain.popup.application.PopupAdminService;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminListResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminUpdateResponseDto;
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

  @Operation(
      summary = "[관리자] 팝업 제보 리스트 조회",
      description =
          """
          관리자가 팝업 제보 리스트를 조회합니다.

          쿼리 파라미터:
          - uuid: 관리자 확인용 user uuid
          - status: 전체/대기/승인/반려, 기본값 전체

          조회 조건:
          - 요청한 uuid의 사용자가 ADMIN 권한이어야 합니다.
          - 종료일(endDate)이 오늘 이후인 제보만 조회합니다.
          - status 응답값은 PENDING/APPROVED/REJECTED enum 문자열입니다.
          """)
  @GetMapping("/popup-submissions")
  public ResponseEntity<List<PopupSubmissionAdminListResponseDto>> getPopupSubmissions(
      @RequestParam(required = false) String uuid,
      @RequestParam(defaultValue = "전체") String status) {
    List<PopupSubmissionAdminListResponseDto> popupSubmissionResponseDtoList =
        popupAdminService.getPopupSubmissions(uuid, status);

    return ResponseEntity.ok(popupSubmissionResponseDtoList);
  }

  @Operation(
      summary = "[관리자] 팝업 제보 상세 조회",
      description =
          """
          관리자가 팝업 제보 상세 정보를 조회합니다.

          쿼리 파라미터:
          - uuid: 관리자 확인용 user uuid

          조회 조건:
          - 요청한 uuid의 사용자가 ADMIN 권한이어야 합니다.
          - popupSubmissionId는 팝업 제보 리스트에서 받은 popup_submission.id입니다.
          - 종료일(endDate)과 status 조건 없이 id로 단건 조회합니다.
          - 사용자가 입력하지 않은 선택값은 null로 응답합니다.
          """)
  @GetMapping("/popup-submissions/{popupSubmissionId}")
  public ResponseEntity<PopupSubmissionAdminDetailResponseDto> getPopupSubmissionDetail(
      @PathVariable Long popupSubmissionId, @RequestParam(required = false) String uuid) {
    PopupSubmissionAdminDetailResponseDto popupSubmissionResponseDto =
        popupAdminService.getPopupSubmissionDetail(uuid, popupSubmissionId);

    return ResponseEntity.ok(popupSubmissionResponseDto);
  }

  @Operation(
      summary = "[관리자] 팝업 제보 승인/반려",
      description =
          """
          관리자가 팝업 제보를 승인하거나 반려합니다.

          쿼리 파라미터:
          - uuid: 관리자 확인용 user uuid

          처리 방식:
          - APPROVED: 요청 본문의 최종 운영 등록값으로 popup, popup_image, popup_recommend를 저장합니다.
          - REJECTED: popup_submission.status만 REJECTED로 변경합니다.
          - popup_submission의 원본 제보 필드는 수정하지 않습니다.
          """)
  @PutMapping("/popup-submissions/{popupSubmissionId}")
  public ResponseEntity<PopupSubmissionAdminUpdateResponseDto> updatePopupSubmission(
      @PathVariable Long popupSubmissionId,
      @RequestParam(required = false) String uuid,
      @RequestBody PopupSubmissionAdminUpdateRequestDto popupSubmissionAdminUpdateRequestDto) {
    PopupSubmissionAdminUpdateResponseDto popupSubmissionAdminUpdateResponseDto =
        popupAdminService.updatePopupSubmission(
            uuid, popupSubmissionId, popupSubmissionAdminUpdateRequestDto);

    return ResponseEntity.ok(popupSubmissionAdminUpdateResponseDto);
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
