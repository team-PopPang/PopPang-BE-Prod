package com.poppang.be.domain.auth.presentation;

import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.domain.auth.application.TokenService;
import com.poppang.be.domain.auth.dto.request.TokenRefreshRequestDto;
import com.poppang.be.domain.auth.dto.response.AccessTokenResponseDto;
import com.poppang.be.domain.auth.dto.response.TokenResponseDto;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class TokenController {

  private final TokenService tokenService;

  @Hidden
  @PostMapping("/token/test")
  public ApiResponse<TokenResponseDto> issueTest(@RequestParam String userUuid) {
    return ApiResponse.ok(tokenService.issueTokens(userUuid));
  }

  @Hidden
  @PostMapping("/refresh")
  public ApiResponse<AccessTokenResponseDto> refresh(
      @RequestBody TokenRefreshRequestDto tokenRefreshRequestDto) {
    return ApiResponse.ok(tokenService.refreshAccessToken(tokenRefreshRequestDto.refreshToken()));
  }
}
