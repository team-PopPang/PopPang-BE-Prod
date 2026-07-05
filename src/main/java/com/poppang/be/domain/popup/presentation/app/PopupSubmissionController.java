package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.domain.popup.application.PopupSubmissionService;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "[POPUP-SUBMISSION]", description = "팝업 제보 API")
@RestController
@RequestMapping("/api/v1/popup-submissions")
@RequiredArgsConstructor
public class PopupSubmissionController {

  private final PopupSubmissionService popupSubmissionService;

  @Operation(
      summary = "팝업스토어 제보 등록",
      description = "multipart/form-data의 request JSON 파트와 images 파일 파트로 팝업스토어 정보를 제보합니다.")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> createPopupSubmission(
      @RequestPart(value = "request", required = false)
          PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto,
      @RequestPart(value = "images", required = false) List<MultipartFile> images) {
    popupSubmissionService.createPopupSubmission(popupSubmissionCreateRequestDto, images);

    return ResponseEntity.ok().build();
  }
}
