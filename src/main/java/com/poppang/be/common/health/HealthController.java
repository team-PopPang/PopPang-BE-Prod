package com.poppang.be.common.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "[공통] 헬스 체크", description = "앱 진입 시 서버 응답 확인")
public class HealthController {

  @Operation(
      summary = "서버 헬스 체크",
      description =
          "인증 없이 서버의 HTTP 요청 처리 가능 여부를 확인합니다. DB, Redis, 소셜 로그인 상태는 검사하지 않습니다. "
              + "정상 응답은 200과 isHealthy=true입니다. 클라이언트는 5xx 응답의 본문에 의존하지 않고, "
              + "타임아웃과 연결 실패도 별도로 처리해야 합니다.")
  @GetMapping
  public ResponseEntity<HealthResponseDto> getHealth() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new HealthResponseDto(true));
  }
}
