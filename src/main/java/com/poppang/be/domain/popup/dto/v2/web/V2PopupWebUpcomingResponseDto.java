package com.poppang.be.domain.popup.dto.v2.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record V2PopupWebUpcomingResponseDto(
    String popupUuid,
    String name,
    String thumbnailUrl,
    String region,
    LocalDate startDate,
    LocalDate endDate,
    @JsonProperty("dDay") int dDay) {}
