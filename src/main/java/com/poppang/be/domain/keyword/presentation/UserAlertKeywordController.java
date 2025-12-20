package com.poppang.be.domain.keyword.presentation;

import com.poppang.be.domain.keyword.application.UserAlertKeywordServiceImpl;
import com.poppang.be.domain.keyword.dto.request.UserAlertKeywordDeleteDto;
import com.poppang.be.domain.keyword.dto.request.UserAlertKeywordRegisterRequestDto;
import com.poppang.be.domain.keyword.dto.response.UserAlertKeywordResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alert-keyword")
@RequiredArgsConstructor
public class UserAlertKeywordController {

  private final UserAlertKeywordServiceImpl userAlertKeywordServiceImpl;

  @Operation(
      summary = "유저 알림 키워드 전체 조회",
      description = "userId를 기준으로 해당 유저가 등록한 알림 키워드 전체를 조회합니다.",
      tags = {"[USER] 알림 키워드 관리"})
  @GetMapping
  public ResponseEntity<List<UserAlertKeywordResponseDto>> getUserAlertKeywords(
      @RequestParam("userUuid") String userUuid) {
    List<UserAlertKeywordResponseDto> userAlertKeywordResponseDtoList =
        userAlertKeywordServiceImpl.getUserAlertKeywordList(userUuid);

    return ResponseEntity.ok(userAlertKeywordResponseDtoList);
  }

  @Operation(
      summary = "알림 키워드 등록",
      description = "유저 ID와 새로운 키워드를 전달하면 키워드를 등록합니다.",
      tags = {"[USER] 알림 키워드 관리"})
  @PostMapping
  public ResponseEntity<Void> registerAlertKeyword(
      @RequestBody UserAlertKeywordRegisterRequestDto userAlertKeywordRegisterRequestDto) {
    userAlertKeywordServiceImpl.registerAlertKeyword(userAlertKeywordRegisterRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "알림 키워드 삭제",
      description = "userId와 keyword를 전달하면 해당 유저의 키워드를 삭제합니다.",
      tags = {"[USER] 알림 키워드 관리"})
  @DeleteMapping
  public ResponseEntity<Void> deleteAlertKeyword(
      @RequestBody UserAlertKeywordDeleteDto userAlertKeywordDeleteDto) {
    userAlertKeywordServiceImpl.deleteAlertKeyword(userAlertKeywordDeleteDto);

    return ResponseEntity.ok().build();
  }
}
