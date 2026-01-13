package com.poppang.be.domain.popup.presentation.app;

import com.poppang.be.domain.popup.application.PopupTotalViewCountService;
import com.poppang.be.domain.popup.dto.app.response.PopupTotalViewCountResponseDto;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[POPUP] Redis 조회수", description = "팝업스토어 Redis 관련 API")
@RestController
@RequestMapping("/api/v1/popup")
@RequiredArgsConstructor
public class PopupTotalViewController {

  private final PopupTotalViewCountService popupTotalViewCountService;

  @Operation(summary = "팝업 상세 진입 시 조회수 증가", description = "특정 팝업의 상세 화면에 진입할 때 조회수를 1 증가시킵니다.")
  @PostMapping("/{popupUuid}/view")
  public ResponseEntity<Void> increment(@PathVariable String popupUuid) {
    long total = popupTotalViewCountService.increment(popupUuid);

    return ResponseEntity.ok().build();
  }

  @Operation(summary = "팝업 총 조회수 조회", description = "특정 팝업의 누적 조회수를 반환합니다.")
  @GetMapping("/{popupUuid}/total-view-count")
  public ResponseEntity<PopupTotalViewCountResponseDto> getTotalViewCount(
      @PathVariable String popupUuid) {
    PopupTotalViewCountResponseDto popupTotalViewCountResponseDto =
        popupTotalViewCountService.getTotalViewCount(popupUuid);

    return ResponseEntity.ok(popupTotalViewCountResponseDto);
  }

  @Hidden
  @GetMapping("/{popupUuid}/view-count")
  public ResponseEntity<Map<String, Long>> getViewCount(@PathVariable String popupUuid) {
    return ResponseEntity.ok(Map.of("viewCount", popupTotalViewCountService.getDelta(popupUuid)));
  }
}
