package com.poppang.be.domain.popup.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.popup.application.V2PopupSubmissionService;
import com.poppang.be.domain.popup.dto.v2.V2PopupSubmissionCreateRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "[POPUP-SUBMISSION v2]", description = "JWT로 인증한 사용자의 팝업 제보 API")
@RestController
@RequestMapping("/api/v2/popup-submissions")
@RequiredArgsConstructor
public class V2PopupSubmissionController {

  private final V2PopupSubmissionService popupSubmissionService;

  @Operation(summary = "팝업스토어 제보 등록")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> createPopupSubmission(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestPart(value = "request", required = false)
          V2PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto,
      @RequestPart(value = "images", required = false) List<MultipartFile> images) {
    popupSubmissionService.createPopupSubmission(
        requireAccessPrincipal(principal), popupSubmissionCreateRequestDto, images);
    return ResponseEntity.ok().build();
  }

  private String requireAccessPrincipal(JwtPrincipal principal) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.ACCESS
        || principal.userUuid() == null
        || principal.userUuid().isBlank()) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return principal.userUuid();
  }
}
