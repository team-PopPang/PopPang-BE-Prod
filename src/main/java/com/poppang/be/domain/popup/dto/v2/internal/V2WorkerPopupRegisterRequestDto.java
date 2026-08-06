package com.poppang.be.domain.popup.dto.v2.internal;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record V2WorkerPopupRegisterRequestDto(
    String name,
    LocalDate startDate,
    LocalDate endDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime openTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime closeTime,
    String address,
    String roadAddress,
    Double longitude,
    Double latitude,
    String region,
    String geocodingQuery,
    String instaPostId,
    String instaPostUrl,
    String captionSummary,
    String caption,
    String mediaType,
    Boolean isActive,
    List<V2WorkerPopupImageUpsertRequestDto> imageList,
    List<Long> recommendIdList) {}
