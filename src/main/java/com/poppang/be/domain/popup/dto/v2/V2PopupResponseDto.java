package com.poppang.be.domain.popup.dto.v2;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.poppang.be.domain.popup.entity.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record V2PopupResponseDto(
    String popupUuid,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        @Schema(description = "운영 시작 시간", example = "10:30", type = "string", format = "time")
        LocalTime openTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        @Schema(description = "운영 종료 시간", example = "22:00", type = "string", format = "time")
        LocalTime closeTime,
    String address,
    String roadAddress,
    String region,
    Double latitude,
    Double longitude,
    String instaPostId,
    String instaPostUrl,
    String captionSummary,
    List<String> imageUrlList,
    MediaType mediaType,
    List<String> recommendList,
    long favoriteCount,
    long viewCount) {}
