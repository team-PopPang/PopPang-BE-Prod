package com.poppang.be.domain.popup.dto.app.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record PopupScrollItemResponseDto(
    String popupUuid,
    String thumbnailUrl,
    String region,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    @JsonProperty("isFavorited") boolean favorited) {}
