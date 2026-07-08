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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

          쿼리 파라미터:
          - uuid: 관리자 확인용 user uuid

          사용 조건:
          - 요청한 uuid의 사용자가 ADMIN 권한이어야 합니다.
          - 존재하지 않는 uuid 또는 popupUuid 요청 시 오류를 반환합니다.

          처리 방식:
          - Popup 엔티티 activated 값을 false로 변경합니다. (dirty checking)
          """)
  @PatchMapping("/popup/{popupUuid}/deactivate")
  public ResponseEntity<Void> deactivatePopup(
      @PathVariable String popupUuid, @RequestParam(required = false) String uuid) {
    popupAdminService.deactivatePopup(uuid, popupUuid);
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

          요청 형식:
          - Content-Type: multipart/form-data
          - request: application/json 파트
          - images: 새로 업로드할 이미지 파일 파트, 필요한 경우 같은 이름으로 반복 전송

          처리 방식:
          - APPROVED: request JSON의 최종 운영 등록값으로 popup, popup_image, popup_recommend를 저장합니다.
          - APPROVED 이미지 처리:
            - sourceType=EXISTING: imageUrl 값을 그대로 popup_image.image_url에 저장합니다.
            - sourceType=UPLOAD: fileIndex 위치의 images 파일을 저장하고 반환 URL을 popup_image.image_url에 저장합니다.
            - imageList에서 제외한 기존 이미지는 운영 popup_image에 저장하지 않습니다.
            - sortOrder가 없으면 imageList 순서대로 0부터 부여합니다.
          - REJECTED: popup_submission.status만 REJECTED로 변경합니다.
          - REJECTED: request JSON만 필요하며 images 파트는 없어도 됩니다.
          - popup_submission의 원본 제보 필드는 수정하지 않습니다.
          """)
  @PutMapping(
      value = "/popup-submissions/{popupSubmissionId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PopupSubmissionAdminUpdateResponseDto> updatePopupSubmission(
      @PathVariable Long popupSubmissionId,
      @RequestParam(required = false) String uuid,
      @RequestPart(value = "request", required = false)
          PopupSubmissionAdminUpdateRequestDto popupSubmissionAdminUpdateRequestDto,
      @RequestPart(value = "images", required = false) List<MultipartFile> images) {
    PopupSubmissionAdminUpdateResponseDto popupSubmissionAdminUpdateResponseDto =
        popupAdminService.updatePopupSubmission(
            uuid, popupSubmissionId, popupSubmissionAdminUpdateRequestDto, images);

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
