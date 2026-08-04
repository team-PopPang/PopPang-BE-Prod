package com.poppang.be.domain.popup.presentation.v2;

import com.poppang.be.domain.popup.application.V2InternalPopupService;
import com.poppang.be.domain.popup.dto.v2.internal.V2WorkerPopupImageUpsertRequestDto;
import com.poppang.be.domain.popup.dto.v2.internal.V2WorkerPopupRegisterRequestDto;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v2/internal/popup")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SERVICE_WORKER')")
public class V2InternalPopupController {

  private final V2InternalPopupService popupService;

  @PostMapping
  public ResponseEntity<Void> registerPopup(@RequestBody V2WorkerPopupRegisterRequestDto request) {
    popupService.registerPopup(request);
    return ResponseEntity.ok().build();
  }

  @PutMapping("/{popupUuid}/images")
  public ResponseEntity<Void> upsertImages(
      @PathVariable String popupUuid,
      @RequestBody List<V2WorkerPopupImageUpsertRequestDto> images) {
    popupService.upsertImages(popupUuid, images);
    return ResponseEntity.ok().build();
  }
}
