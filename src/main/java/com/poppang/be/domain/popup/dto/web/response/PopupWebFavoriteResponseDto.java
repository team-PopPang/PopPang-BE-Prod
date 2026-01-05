package com.poppang.be.domain.popup.dto.web.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class PopupWebFavoriteResponseDto {

    private String popupUuid;
    private String name;
    private String thumbnailUrl;
    private String region;
    private LocalDate startDate;
    private LocalDate endDate;

    @Builder
    public PopupWebFavoriteResponseDto(String popupUuid, String name, String thumbnailUrl, String region, LocalDate startDate, LocalDate endDate) {
        this.popupUuid = popupUuid;
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.region = region;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
