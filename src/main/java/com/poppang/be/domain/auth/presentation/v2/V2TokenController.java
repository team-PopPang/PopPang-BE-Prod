package com.poppang.be.domain.auth.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.auth.application.V2TokenService;
import com.poppang.be.domain.auth.dto.v2.request.V2TokenRefreshRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v2/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2TokenController {

  private final V2TokenService tokenService;

  @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<V2TokenResponseDto>> refresh(
      @RequestBody(required = false) V2TokenRefreshRequestDto request) {
    String refreshToken = request == null ? null : request.refreshToken();
    V2TokenResponseDto response = tokenService.refresh(refreshToken);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(ApiResponse.ok(response));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@AuthenticationPrincipal JwtPrincipal principal) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.ACCESS
        || principal.userUuid() == null
        || principal.userUuid().isBlank()
        || principal.sessionId() == null
        || principal.sessionId().isBlank()) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    tokenService.logout(principal.userUuid(), principal.sessionId());
    return ResponseEntity.ok().build();
  }
}
