package com.poppang.be.domain.auth.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.common.security.JwtAuthenticationDetails;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.auth.apple.application.V2AppleAuthService;
import com.poppang.be.domain.auth.dto.v2.request.V2AppleMobileLoginRequestDto;
import com.poppang.be.domain.auth.dto.v2.request.V2SignupRequestDto;
import com.poppang.be.domain.auth.dto.v2.response.V2SocialAuthResponseDto;
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
@RequestMapping(value = "/api/v2/auth/apple", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2AppleAuthController {

  private final V2AppleAuthService authService;

  @PostMapping(value = "/mobile/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<V2SocialAuthResponseDto>> mobileLogin(
      @RequestBody(required = false) V2AppleMobileLoginRequestDto request) {
    return noStore(
        authService.mobileLogin(
            request == null ? null : request.authorizationCode(),
            request == null ? null : request.rawNonce()));
  }

  @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<V2SocialAuthResponseDto>> signup(
      @AuthenticationPrincipal JwtPrincipal principal,
      Authentication authentication,
      @RequestBody(required = false) V2SignupRequestDto request) {
    JwtAuthenticationDetails details = requireSignupAuthentication(principal, authentication);
    return noStore(authService.signup(principal.userUuid(), request, details));
  }

  private JwtAuthenticationDetails requireSignupAuthentication(
      JwtPrincipal principal, Authentication authentication) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.SIGNUP
        || principal.userUuid() == null
        || principal.userUuid().isBlank()
        || authentication == null
        || !(authentication.getDetails() instanceof JwtAuthenticationDetails details)) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return details;
  }

  private ResponseEntity<ApiResponse<V2SocialAuthResponseDto>> noStore(
      V2SocialAuthResponseDto response) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(ApiResponse.ok(response));
  }
}
