package com.poppang.be.domain.popup.dto.v2.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record V2PopupWebDetailResponseDto(
    String popupUuid,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime openTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime closeTime,
    String address,
    String roadAddress,
    String region,
    String instaPostUrl,
    String captionSummary,
    List<String> imageUrlList,
    List<String> recommendList,
    long favoriteCount,
    long viewCount) {}
