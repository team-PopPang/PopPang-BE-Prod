package com.poppang.be.domain.users.presentation.v2;

import com.poppang.be.domain.users.application.V2InternalUsersService;
import com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordGroupResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordResponseDto;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v2/internal/user/with-alert-keyword")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SERVICE_WORKER')")
public class V2InternalUsersController {

  private final V2InternalUsersService usersService;

  @GetMapping(value = "/a", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<V2WorkerUserKeywordResponseDto>> getUsersWithAlertKeyword() {
    return ResponseEntity.ok(usersService.getUsersWithAlertKeyword());
  }

  @GetMapping(value = "/b", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<V2WorkerUserKeywordGroupResponseDto>> getUsersWithAlertKeywordGroup() {
    return ResponseEntity.ok(usersService.getUsersWithAlertKeywordGroup());
  }
}
