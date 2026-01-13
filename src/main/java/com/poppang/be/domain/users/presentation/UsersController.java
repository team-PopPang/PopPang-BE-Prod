package com.poppang.be.domain.users.presentation;

import com.poppang.be.domain.popup.dto.app.response.UserUpdateFcmTokenResquestDto;
import com.poppang.be.domain.users.application.UsersService;
import com.poppang.be.domain.users.dto.request.ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.request.UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.response.*;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[USER] 공통", description = "유저 관련 API")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UsersController {

  private final UsersService usersService;

  @Operation(summary = "유저 정보 조회", description = "userUuid를 기준으로 유저의 기본 정보를 조회합니다.")
  @GetMapping("/{userUuid}")
  public ResponseEntity<GetUserResponseDto> getUserInfo(@PathVariable String userUuid) {
    GetUserResponseDto getUserResponseDto = usersService.getUserInfo(userUuid);

    return ResponseEntity.ok(getUserResponseDto);
  }

  @Operation(
      summary = "사용자 알림 상태 수정 (isAlerted)",
      description =
          """
                특정 사용자의 알림 수신 상태(alerted)를 변경합니다.
                PATCH 메서드를 사용하여 부분 업데이트를 수행합니다.
                예를 들어, 알림을 켜려면 `true`, 끄려면 `false`로 요청합니다.
                """)
  @PatchMapping("{userUuid}/alert-status")
  public ResponseEntity<UpdateAlertStatusResponseDto> updateAlertStatus(
      @PathVariable String userUuid,
      @RequestBody UpdateAlertStatusRequestDto updateAlertStatusRequestDto) {
    UpdateAlertStatusResponseDto updateAlertStatusResponseDto =
        usersService.updateAlertStatus(userUuid, updateAlertStatusRequestDto);

    return ResponseEntity.ok(updateAlertStatusResponseDto);
  }

  @Operation(summary = "닉네임 중복 검사", description = "입력된 닉네임이 이미 존재하는지 여부를 반환합니다.")
  @GetMapping("/nickname/duplicated")
  public ResponseEntity<NicknameDuplicateResponseDto> checkNicknameDuplicated(
      @RequestParam String nickname) {
    NicknameDuplicateResponseDto nicknameDuplicateResponseDto =
        usersService.checkNicknameDuplicated(nickname);

    return ResponseEntity.ok(nicknameDuplicateResponseDto);
  }

  @Operation(summary = "닉네임 변경", description = "사용자의 닉네임을 변경합니다.")
  @PatchMapping("/{userUuid}")
  public ResponseEntity<Void> changeNickname(
      @PathVariable String userUuid,
      @RequestBody ChangeNicknameRequestDto changeNicknameRequestDto) {

    usersService.changeNickname(userUuid, changeNicknameRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "유저 탈퇴 기능 (hard-delete)",
      description = "유저 회원탈퇴를 진행합니다. (hard-delete)라서 데이터는 복구할 수 없습니다.")
  @DeleteMapping("{userUuid}/hard-delete")
  public ResponseEntity<Void> hardDeleteUser(@PathVariable String userUuid) {
    usersService.hardDeleteUser(userUuid);

    return ResponseEntity.ok().build();
  }

  @Hidden
  @PatchMapping("/{userUuid}/soft-delete")
  public ResponseEntity<Void> softDeleteUser(@PathVariable String userUuid) {
    usersService.softDeleteUser(userUuid);

    return ResponseEntity.ok().build();
  }

  @Hidden
  @PatchMapping("/{userUuid}/resotre")
  public ResponseEntity<Void> restoreUser(@PathVariable String userUuid) {
    usersService.restoreUser(userUuid);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "사용자의 FCM 토큰 중복 여부 확인",
      description = "특정 사용자의 기존 FCM 토큰과 입력받은 FCM 토큰이 동일한지 여부를 확인합니다.")
  @GetMapping("/{userUuid}/fcm-token/duplicate-check")
  public ResponseEntity<Boolean> isFcmTokenDuplicated(
      @PathVariable String userUuid, @RequestParam String fcmToken) {
    boolean fcmTokenDuplicated = usersService.isFcmTokenDuplicated(userUuid, fcmToken);

    return ResponseEntity.ok(fcmTokenDuplicated);
  }

  @Operation(
      summary = "사용자의 FCM 토큰 갱신",
      description = "해당 사용자의 FCM 토큰을 새로운 값으로 업데이트합니다. 같은 요청을 여러 번 보내도 결과는 동일합니다.")
  @PutMapping("/{userUuid}/fcm-token/update")
  public ResponseEntity<Void> updateFcmToken(
      @PathVariable String userUuid,
      @RequestBody UserUpdateFcmTokenResquestDto userUpdateFcmTokenResquestDto) {
    usersService.updateFcmToken(userUuid, userUpdateFcmTokenResquestDto);

    return ResponseEntity.ok().build();
  }

  @Tag(name = "[CRON]", description = "CRON 관련 API")
  @Operation(summary = "cron(알림 서비스)에 필요한 유저 정보 + 알림 키워드 a안")
  @GetMapping("/with-alert-keyword/a")
  public ResponseEntity<List<UserWithKeywordListResponseDto>> getUserWithKeywordList() {
    List<UserWithKeywordListResponseDto> userWithKeywordListResponseDtoList =
        usersService.getUserWithKeywordList();

    return ResponseEntity.ok(userWithKeywordListResponseDtoList);
  }

  @Tag(name = "[CRON]", description = "CRON 관련 API")
  @Operation(summary = "cron(알림 서비스)에 필요한 유저 정보 + 알림 키워드 b안")
  @GetMapping("/with-alert-keyword/b")
  public ResponseEntity<List<UserWithKeywordListResponseDtoB>> getUserWithKeywordListB() {
    List<UserWithKeywordListResponseDtoB> userWithKeywordListResponseDtoBList =
        usersService.getUserWithKeywordListB();

    return ResponseEntity.ok(userWithKeywordListResponseDtoBList);
  }
}
