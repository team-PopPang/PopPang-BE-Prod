package com.poppang.be.domain.popup.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.poppang.be.domain.popup.entity.MediaType;
import com.poppang.be.domain.popup.entity.Popup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class PopupResponseDto {

    private String popupUuid;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Schema(description = "운영 시작 시간", example = "10:30", type = "string", format = "time")
    private LocalTime openTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    @Schema(description = "운영 종료 시간", example = "22:00", type = "string", format = "time")
    private LocalTime closeTime;

    private String address;
    private String roadAddress;
    private String region;
    private Double latitude;
    private Double longitude;
    private String instaPostId;
    private String instaPostUrl;
    private String captionSummary;

    private List<String> imageUrlList;
    private MediaType mediaType;
    private List<String> recommendList;
    private long favoriteCount;
    private long viewCount;

    @Builder
    public PopupResponseDto(String popupUuid,
                            String name,
                            LocalDate startDate,
                            LocalDate endDate,
                            LocalTime openTime,
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
                            long viewCount) {

        this.popupUuid = popupUuid;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.address = address;
        this.roadAddress = roadAddress;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.instaPostId = instaPostId;
        this.instaPostUrl = instaPostUrl;
        this.captionSummary = captionSummary;
        this.imageUrlList = imageUrlList;
        this.mediaType = mediaType;
        this.recommendList = recommendList;
        this.favoriteCount = favoriteCount;
        this.viewCount = viewCount;
    }

    public static PopupResponseDto from(Popup popup) {
        return PopupResponseDto.builder()
                .popupUuid(popup.getUuid())
                .name(popup.getName())
                .startDate(popup.getStartDate())
                .endDate(popup.getEndDate())
                .openTime(popup.getOpenTime())
                .closeTime(popup.getCloseTime())
                .address(popup.getAddress())
                .roadAddress(popup.getRoadAddress())
                .region(popup.getRegion())
                .latitude(popup.getLatitude())
                .longitude(popup.getLongitude())
                .instaPostId(popup.getInstaPostId())
                .instaPostUrl(popup.getInstaPostUrl())
                .build();
    }

}
