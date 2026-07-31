package com.poppang.be.domain.users.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.users.application.V2UsersService;
import com.poppang.be.domain.users.dto.v2.request.V2ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateFcmTokenRequestDto;
import com.poppang.be.domain.users.dto.v2.response.V2NicknameDuplicateResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UpdateAlertStatusResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "[USER v2] 본인", description = "JWT로 인증한 사용자의 본인 정보 API")
@RequestMapping(value = "/api/v2/user", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2UsersController {

  private final V2UsersService usersService;

  @Operation(summary = "내 정보 조회")
  @GetMapping
  public ResponseEntity<ApiResponse<V2UserResponseDto>> getUser(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(ApiResponse.ok(usersService.getUser(requireUserUuid(principal))));
  }

  @Operation(summary = "내 알림 수신 상태 변경")
  @PatchMapping(value = "/alert-status", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiResponse<V2UpdateAlertStatusResponseDto>> updateAlertStatus(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestBody(required = false) V2UpdateAlertStatusRequestDto request) {
    return ResponseEntity.ok(
        ApiResponse.ok(usersService.updateAlertStatus(requireUserUuid(principal), request)));
  }

  @Operation(summary = "닉네임 중복 검사")
  @GetMapping("/nickname/duplicated")
  public ResponseEntity<ApiResponse<V2NicknameDuplicateResponseDto>> checkNicknameDuplicated(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(required = false) String nickname) {
    return ResponseEntity.ok(
        ApiResponse.ok(usersService.checkNicknameDuplicated(requireUserUuid(principal), nickname)));
  }

  @Operation(summary = "내 닉네임 변경")
  @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> changeNickname(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestBody(required = false) V2ChangeNicknameRequestDto request) {
    usersService.changeNickname(requireUserUuid(principal), request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "회원 탈퇴", description = "인증한 사용자를 비활성화합니다.")
  @DeleteMapping
  public ResponseEntity<Void> softDelete(@AuthenticationPrincipal JwtPrincipal principal) {
    usersService.softDelete(requireUserUuid(principal));
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "내 FCM Token 갱신")
  @PutMapping(value = "/fcm-token", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> updateFcmToken(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestBody(required = false) V2UpdateFcmTokenRequestDto request) {
    usersService.updateFcmToken(requireUserUuid(principal), request);
    return ResponseEntity.ok().build();
  }

  private String requireUserUuid(JwtPrincipal principal) {
    if (principal == null
        || principal.tokenType() != JwtTokenType.ACCESS
        || principal.userUuid() == null
        || principal.userUuid().isBlank()) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return principal.userUuid();
  }
}
