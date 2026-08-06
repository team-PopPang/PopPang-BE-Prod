package com.poppang.be.domain.alert.presentation.v2;

import com.poppang.be.domain.alert.application.V2InternalUserAlertService;
import com.poppang.be.domain.alert.dto.v2.V2WorkerUserAlertRegisterRequestDto;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v2/internal/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SERVICE_WORKER')")
public class V2InternalUserAlertController {

  private final V2InternalUserAlertService userAlertService;

  @PostMapping("/{userUuid}/alert")
  public ResponseEntity<Void> registerUserAlert(
      @PathVariable String userUuid, @RequestBody V2WorkerUserAlertRegisterRequestDto request) {
    userAlertService.registerUserAlert(userUuid, request);
    return ResponseEntity.ok().build();
  }
}
