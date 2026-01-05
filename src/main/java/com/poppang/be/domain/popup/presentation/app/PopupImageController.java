package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.domain.popup.application.PopupImageServiceImpl;
import com.poppang.be.domain.popup.dto.app.request.PopupImageUpsertRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/popup")
@RequiredArgsConstructor
public class PopupImageController {

  private final PopupImageServiceImpl popupImageServiceImpl;

  @Tag(name = "[CRON]", description = "CRON 관련 API")
  @Operation(summary = "팝업 이미지 등록/수정 (Upsert)", description = "특정 팝업(UUID 기준)의 이미지를 등록 또는 수정합니다 ")
  @PutMapping("/{popupUuid}/images")
  public ResponseEntity<Void> upsertImage(
      @PathVariable String popupUuid,
      @RequestBody List<PopupImageUpsertRequestDto> popupImageUpsertRequestDtoList) {
    popupImageServiceImpl.upsertImages(popupUuid, popupImageUpsertRequestDtoList);

    return ResponseEntity.ok().build();
  }
}
