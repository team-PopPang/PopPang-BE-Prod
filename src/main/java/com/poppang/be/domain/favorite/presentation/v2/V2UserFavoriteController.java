package com.poppang.be.domain.favorite.presentation.v2;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtTokenType;
import com.poppang.be.common.response.ApiResponse;
import com.poppang.be.common.security.JwtPrincipal;
import com.poppang.be.domain.favorite.application.V2UserFavoriteService;
import com.poppang.be.domain.favorite.dto.v2.V2FavoriteCountResponseDto;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.favorite.dto.v2.V2FavoriteRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "[FAVORITE v2] 찜", description = "JWT로 인증한 사용자의 찜 API")
@RequestMapping(value = "/api/v2/favorite", produces = MediaType.APPLICATION_JSON_VALUE)
public class V2UserFavoriteController {

  private final V2UserFavoriteService favoriteService;

  @Operation(summary = "내 찜 등록")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> registerFavorite(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestBody V2FavoriteRequestDto request) {
    favoriteService.registerFavorite(requireUserUuid(principal), request.popupUuid());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "내 찜 삭제")
  @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteFavorite(
      @AuthenticationPrincipal JwtPrincipal principal, @RequestBody V2FavoriteRequestDto request) {
    favoriteService.deleteFavorite(requireUserUuid(principal), request.popupUuid());
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "팝업 찜 수 조회")
  @GetMapping("/count/{popupUuid}")
  public ResponseEntity<ApiResponse<V2FavoriteCountResponseDto>> getFavoriteCount(
      @AuthenticationPrincipal JwtPrincipal principal, @PathVariable String popupUuid) {
    requireUserUuid(principal);
    return ResponseEntity.ok(ApiResponse.ok(favoriteService.getFavoriteCount(popupUuid)));
  }

  @Operation(summary = "내가 찜한 팝업 목록 조회")
  @GetMapping("/popup")
  public ResponseEntity<ApiResponse<List<V2FavoritePopupResponseDto>>> getFavoritePopupList(
      @AuthenticationPrincipal JwtPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.ok(favoriteService.getFavoritePopupList(requireUserUuid(principal))));
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
