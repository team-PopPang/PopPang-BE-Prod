package com.poppang.be.common.health;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record HealthResponseDto(
    @JsonProperty("isHealthy")
        @Schema(
            description = "서버 HTTP 요청 처리 가능 여부. 정상 응답에서는 항상 true입니다.",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isHealthy) {}
