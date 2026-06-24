package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.domain.popup.application.PopupSubmissionService;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[POPUP-SUBMISSION]", description = "팝업 제보 API")
@RestController
@RequestMapping("/api/v1/popup-submissions")
@RequiredArgsConstructor
public class PopupSubmissionController {

  private final PopupSubmissionService popupSubmissionService;

  @Operation(summary = "팝업스토어 제보 등록", description = "사용자가 팝업스토어 정보를 제보합니다.")
  @PostMapping
  public ResponseEntity<Void> createPopupSubmission(
      @RequestBody PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto) {
    popupSubmissionService.createPopupSubmission(popupSubmissionCreateRequestDto);

    return ResponseEntity.ok().build();
  }
}
