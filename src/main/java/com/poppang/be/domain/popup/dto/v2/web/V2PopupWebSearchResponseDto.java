package com.poppang.be.domain.popup.dto.v2.web;

import java.time.LocalDate;

public record V2PopupWebSearchResponseDto(
    String popupUuid,
    String name,
    String thumbnailUrl,
    String region,
    LocalDate startDate,
    LocalDate endDate) {}
