package com.poppang.be.domain.favorite.presentation;

import com.poppang.be.domain.favorite.application.UserFavoriteServiceImpl;
import com.poppang.be.domain.favorite.dto.request.UserFavoriteDeleteRequestDto;
import com.poppang.be.domain.favorite.dto.request.UserFavoriteRegisterRequestDto;
import com.poppang.be.domain.favorite.dto.response.FavoriteCountResponseDto;
import com.poppang.be.domain.popup.dto.response.PopupUserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/favorite")
@RequiredArgsConstructor
public class UserFavoriteController {

  private final UserFavoriteServiceImpl userFavoriteServiceImpl;

  @Operation(
      summary = "찜 등록",
      description = "유저가 특정 팝업을 찜합니다.",
      tags = {"[FAVORITE] 공통"})
  @PostMapping
  public ResponseEntity<Void> registerFavorite(
      @RequestBody UserFavoriteRegisterRequestDto userFavoriteRegisterRequestDto) {
    userFavoriteServiceImpl.registerFavorite(userFavoriteRegisterRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "찜 삭제",
      description = "유저가 찜한 팝업을 취소합니다.",
      tags = {"[FAVORITE] 공통"})
  @DeleteMapping
  public ResponseEntity<Void> deleteFavorite(
      @RequestBody UserFavoriteDeleteRequestDto userFavoriteDeleteRequestDto) {
    userFavoriteServiceImpl.deleteFavorite(userFavoriteDeleteRequestDto);

    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "팝업별 찜 수 조회",
      description = "특정 팝업이 받은 전체 찜 개수를 조회합니다.",
      tags = {"[FAVORITE] 공통"})
  @GetMapping("/count/{popupUuid}")
  public ResponseEntity<FavoriteCountResponseDto> getFavoriteCount(
      @PathVariable("popupUuid") String popupUuid) {
    FavoriteCountResponseDto favoriteCountResponseDto =
        userFavoriteServiceImpl.getFavoriteCount(popupUuid);

    return ResponseEntity.ok(favoriteCountResponseDto);
  }

  @Operation(
      summary = "유저가 찜한 팝업 목록 조회",
      description = "특정 유저가 찜한 팝업 리스트를 조회합니다.",
      tags = {"[FAVORITE] 공통"})
  @GetMapping("/popup/{userUuid}")
  public ResponseEntity<List<PopupUserResponseDto>> getFavoritePopupList(
      @PathVariable("userUuid") String userUuid) {
    List<PopupUserResponseDto> userFavoritePopupResponseDtoList =
        userFavoriteServiceImpl.getFavoritePopupList(userUuid);

    return ResponseEntity.ok(userFavoritePopupResponseDtoList);
  }
}
