package com.poppang.be.domain.alert.presentation;

import com.poppang.be.domain.alert.application.UserAlertService;
import com.poppang.be.domain.alert.dto.request.UserAlertDeleteRequestDto;
import com.poppang.be.domain.alert.dto.request.UserAlertRegisterRequestDto;
import com.poppang.be.domain.alert.dto.response.UserAlertResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[ALERT] 회원", description = "회원 알림 팝업 관리 API")
@RestController
@RequestMapping("/api/v1/users/{userUuid}/alert")
@RequiredArgsConstructor
public class UserAlertController {

  private final UserAlertService userAlertService;

  @Operation(
      summary = "알림 받은 팝업 등록",
      description =
          """
                    특정 유저에 대해, 알림으로 전달된 팝업을 user_alert 테이블에 저장합니다.
                    - path 변수의 userUuid로 유저를 식별합니다.
                    - RequestBody의 popupUuid로 팝업을 식별합니다.
                    - 동일 (user, popup) 조합은 한 번만 저장되며, 중복 요청 시 무시됩니다.
                    """)
  @PostMapping
  public ResponseEntity<Void> registerUserAlert(
      @PathVariable String userUuid,
      @RequestBody UserAlertRegisterRequestDto userAlertRegisterRequestDto) {
    userAlertService.registerUserAlert(userUuid, userAlertRegisterRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "알림 받은 팝업 삭제",
      description =
          """
                    특정 유저의 알림 받은 팝업 기록을 삭제합니다.
                    - path 변수의 userUuid로 유저를 식별합니다.
                    - RequestBody의 popupUuid에 해당하는 알림 기록만 삭제합니다.
                    - 존재하지 않는 경우 예외 또는 무시(서비스 로직에 따름)됩니다.
                    """)
  @DeleteMapping
  public ResponseEntity<Void> deleteUserAlert(
      @PathVariable String userUuid,
      @RequestBody UserAlertDeleteRequestDto userAlertDeleteRequestDto) {
    userAlertService.deleteUserAlert(userUuid, userAlertDeleteRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "유저별 알림 받은 팝업 목록 조회",
      description =
          """
                    특정 유저가 '알림 받은 팝업' 탭에서 확인할 팝업 목록을 조회합니다.
                    - path 변수의 userUuid로 유저를 식별합니다.
                    - user_alert 테이블에서 해당 유저의 레코드를 조회한 뒤,
                      PopupUserResponseDto 리스트 형태로 반환합니다.
                    - 각 팝업별로 isFavorited, favoriteCount, viewCount 등 개인화 정보가 포함됩니다.
                    """)
  @GetMapping("/popups")
  public ResponseEntity<List<UserAlertResponseDto>> getUserAlertPopupList(
      @PathVariable String userUuid) {
    List<UserAlertResponseDto> userAlertPopupList =
        userAlertService.getUserAlertPopupList(userUuid);

    return ResponseEntity.ok(userAlertPopupList);
  }

  @Operation(
      summary = "알림 읽음 처리 API",
      description =
          """
                    특정 유저(userUuid)가 받은 팝업 알림 중,
                    특정 팝업(popupUuid)에 대한 알림을 '읽음(readAt)' 상태로 변경합니다.

                    • userUuid는 URL Path에서 전달됩니다.
                    • popupUuid는 읽음 처리 대상 팝업의 UUID로, RequestParam으로 전달합니다.
                    • 이미 읽은 알림(readAt IS NOT NULL)일 경우, 업데이트하지 않고 그대로 유지됩니다.
                    • 존재하지 않는 user 또는 popup, 혹은 알림 관계가 없을 경우 예외가 발생합니다.
                    """)
  @PatchMapping("/read")
  public ResponseEntity<Void> readUserAlertPopup(
      @PathVariable String userUuid, @RequestParam String popupUuid) {
    userAlertService.readUserAlertPopup(userUuid, popupUuid);

    return ResponseEntity.ok().build();
  }
}
