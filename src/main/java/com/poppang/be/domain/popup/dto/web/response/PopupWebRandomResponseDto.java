package com.poppang.be.domain.popup.dto.web.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupWebRandomResponseDto {
    private String popupUuid;
    private String name;
    private String thumbnailUrl;

    @Builder
    public PopupWebRandomResponseDto(String popupUuid, String name, String thumbnailUrl) {
        this.popupUuid = popupUuid;
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
    }
}
