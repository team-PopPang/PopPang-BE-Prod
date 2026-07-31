package com.poppang.be.domain.auth.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2KakaoAuthResponseDto;
import com.poppang.be.domain.auth.kakao.application.V2KakaoAuthService;
import com.poppang.be.domain.auth.kakao.dto.request.KakaoAppLoginRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v2/auth/kakao", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2KakaoAuthController {

  private final V2KakaoAuthService authService;

  @PostMapping(value = "/mobile/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<V2KakaoAuthResponseDto>> mobileLogin(
      @RequestBody(required = false) KakaoAppLoginRequestDto request) {
    String providerAccessToken = request == null ? null : request.getAccessToken();
    return noStore(authService.mobileLogin(providerAccessToken));
  }

  @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<V2KakaoAuthResponseDto>> signup(
      @AuthenticationPrincipal JwtPrincipal principal,
      Authentication authentication,
      @RequestBody(required = false) V2SignupRequestDto request) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.SIGNUP
        || principal.userUuid() == null
        || principal.userUuid().isBlank()
        || authentication == null
        || !(authentication.getDetails() instanceof JwtAuthenticationDetails details)) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return noStore(authService.signup(principal.userUuid(), request, details));
  }

  private ResponseEntity<ApiResponse<V2KakaoAuthResponseDto>> noStore(
      V2KakaoAuthResponseDto response) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(ApiResponse.ok(response));
  }
}
