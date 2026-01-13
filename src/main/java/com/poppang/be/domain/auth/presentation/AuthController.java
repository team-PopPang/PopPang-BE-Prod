package com.poppang.be.domain.auth.presentation;

import com.poppang.be.domain.auth.apple.application.AppleAuthService;
import com.poppang.be.domain.auth.apple.dto.request.AppleAppLoginRequestDto;
import com.poppang.be.domain.auth.application.AuthService;
import com.poppang.be.domain.auth.dto.request.AutoLoginRequestDto;
import com.poppang.be.domain.auth.dto.response.LoginResponseDto;
import com.poppang.be.domain.auth.dto.response.SignupResponseDto;
import com.poppang.be.domain.auth.google.application.GoogleAuthService;
import com.poppang.be.domain.auth.google.dto.request.GoogleAppLoginRequestDto;
import com.poppang.be.domain.auth.kakao.application.KakaoAuthService;
import com.poppang.be.domain.auth.kakao.dto.request.KakaoAppLoginRequestDto;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final KakaoAuthService kakaoAuthService;
  private final AppleAuthService appleAuthService;
  private final GoogleAuthService googleAuthService;
  private final AuthService authService;

  /* ---------- 웹(브라우저)용: GET code 콜백 ---------- */

  @Hidden
  @Operation(
      summary = "[WEB] 카카오 로그인 콜백",
      description = "카카오 인가 코드(code)를 통해 로그인 처리 후 사용자 정보를 반환합니다.",
      tags = {"[AUTH] 카카오"})
  @GetMapping("/kakao/login")
  public ResponseEntity<LoginResponseDto> kakaoWebLogin(@RequestParam("code") String authCode) {
    LoginResponseDto loginResponseDto = kakaoAuthService.webLogin(authCode);
    return ResponseEntity.ok(loginResponseDto);
  }

  @Hidden
  @Operation(
      summary = "[WEB] 애플 로그인 콜백",
      description = "애플 인가 코드(code)를 통해 로그인 처리 후 사용자 정보를 반환합니다.",
      tags = {"[AUTH] 애플"})
  @GetMapping("/apple/login")
  public ResponseEntity<LoginResponseDto> appleWebLogin(@RequestParam("code") String authCode) {
    LoginResponseDto loginResponseDto = appleAuthService.webLogin(authCode);
    return ResponseEntity.ok(loginResponseDto);
  }

  @Hidden
  @Operation(
      summary = "[WEB] 구글 로그인 콜백",
      description = "구글 인가 코드(code)를 통해 로그인 처리 후 사용자 정보를 반환합니다.",
      tags = {"[AUTH] 구글"})
  @GetMapping("/google/login")
  public ResponseEntity<LoginResponseDto> googleWebLogin(@RequestParam("code") String authCode) {
    LoginResponseDto loginResponseDto = googleAuthService.webLogin(authCode);
    return ResponseEntity.ok(loginResponseDto);
  }

  /* ---------- 앱(Native)용: POST JSON 바디 ---------- */
  @Hidden
  @Operation(
      summary = "[APP] 카카오 로그인",
      description = "카카오 앱에서 발급받은 액세스 토큰을 사용해 로그인합니다.",
      tags = {"[AUTH] 카카오"})
  @PostMapping("/kakao/mobile/login")
  public ResponseEntity<LoginResponseDto> kakaoMobileLogin(
      @RequestBody KakaoAppLoginRequestDto kakaoAppLoginRequestDto) {
    LoginResponseDto loginResponseDto = kakaoAuthService.mobileLogin(kakaoAppLoginRequestDto);
    return ResponseEntity.ok(loginResponseDto);
  }

  @Hidden
  @Operation(
      summary = "[APP] 애플 로그인",
      description = "애플 앱에서 발급받은 토큰/ID 토큰을 사용해 로그인합니다.",
      tags = {"[AUTH] 애플"})
  @PostMapping("/apple/mobile/login")
  public ResponseEntity<LoginResponseDto> appleMobileLogin(
      @RequestBody AppleAppLoginRequestDto appleAppLoginRequestDto) {
    LoginResponseDto loginResponseDto = appleAuthService.mobileLogin(appleAppLoginRequestDto);
    return ResponseEntity.ok(loginResponseDto);
  }

  @Hidden
  @Operation(
      summary = "[APP] 구글 로그인",
      description = "구글 앱에서 발급받은 id_token 을 사용해 로그인합니다.",
      tags = {"[AUTH] 구글"})
  @PostMapping("/google/mobile/login")
  public ResponseEntity<LoginResponseDto> googleMobileLogin(
      @RequestBody GoogleAppLoginRequestDto googleAppLoginRequestDto) {
    LoginResponseDto loginResponseDto = googleAuthService.mobileLogin(googleAppLoginRequestDto);
    return ResponseEntity.ok(loginResponseDto);
  }

  /* ---------- 앱 자동 로그인 ---------- */

  @Hidden
  @Operation(
      summary = "자동 로그인",
      description = "앱 로컬에 저장된 uuid로 자동 로그인합니다.",
      tags = {"[AUTH] 공통"})
  @PostMapping("/autoLogin")
  public ResponseEntity<LoginResponseDto> autoLogin(
      @RequestBody AutoLoginRequestDto autoLoginRequestDto) {
    LoginResponseDto loginResponseDto = authService.autoLogin(autoLoginRequestDto);
    return ResponseEntity.ok(loginResponseDto);
  }

  /* ---------- 회원가입 ---------- */

  @Hidden
  @Operation(
      summary = "카카오 회원가입",
      description = "카카오 로그인 이후 닉네임/알림/키워드/추천 카테고리를 등록합니다.",
      tags = {"[AUTH] 카카오"})
  @PostMapping("/kakao/signup")
  public ResponseEntity<SignupResponseDto> kakaoSignup(
      @RequestBody SignupRequestDto signupRequestDto) {
    SignupResponseDto signupResponseDto = kakaoAuthService.signup(signupRequestDto);
    return ResponseEntity.ok(signupResponseDto);
  }

  @Hidden
  @Operation(
      summary = "애플 회원가입",
      description = "애플 로그인 이후 회원가입을 완료합니다.",
      tags = {"[AUTH] 애플"})
  @PostMapping("/apple/signup")
  public ResponseEntity<SignupResponseDto> appleSignup(
      @RequestBody SignupRequestDto signupRequestDto) {
    SignupResponseDto signupResponseDto = appleAuthService.signup(signupRequestDto);
    return ResponseEntity.ok(signupResponseDto);
  }

  @Hidden
  @Operation(
      summary = "구글 회원가입",
      description = "구글 로그인 이후 회원가입을 완료합니다.",
      tags = {"[AUTH] 구글"})
  @PostMapping("/google/signup")
  public ResponseEntity<SignupResponseDto> googleSignup(
      @RequestBody SignupRequestDto signupRequestDto) {
    SignupResponseDto signupResponseDto = googleAuthService.signup(signupRequestDto);
    return ResponseEntity.ok(signupResponseDto);
  }
}
