package com.poppang.be.domain.popup.dto.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record V2UserPopupScrollItemResponseDto(
    String popupUuid,
    String thumbnailUrl,
    String region,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    @JsonProperty("isFavorited") boolean favorited) {}
