package com.poppang.be.domain.auth.presentation.v2;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.auth.application.V2QaTokenService;
import com.poppang.be.domain.auth.dto.v2.response.V2TokenResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v2/test-auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "v2 QA 인증", description = "고정 QA 계정 토큰 발급")
public class V2QaTokenController {

  private final V2QaTokenService qaTokenService;

  @PostMapping("/token")
  @Operation(
      summary = "QA 계정 토큰 발급",
      description = "MEMBER 또는 ADMIN 고정 계정의 Access Token과 Refresh Token을 발급합니다.",
      security = @SecurityRequirement(name = "qaApiKeyAuth"))
  public ResponseEntity<ApiResponse<V2TokenResponseDto>> issueTokens(
      @Parameter(
              required = true,
              description = "발급할 고정 QA 계정",
              schema = @Schema(allowableValues = {"MEMBER", "ADMIN"}))
          @RequestParam(required = false)
          String account) {
    V2TokenResponseDto response = qaTokenService.issueTokens(account);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(ApiResponse.ok(response));
  }
}
