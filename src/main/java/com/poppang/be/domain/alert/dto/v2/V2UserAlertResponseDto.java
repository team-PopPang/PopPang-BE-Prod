package com.poppang.be.domain.alert.dto.v2;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.popup.entity.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record V2UserAlertResponseDto(
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
    long viewCount,
    @JsonProperty("isFavorited") boolean favorited,
    @JsonProperty("isRead") boolean read) {

  public static V2UserAlertResponseDto from(V2FavoritePopupResponseDto popup, boolean isRead) {
    return new V2UserAlertResponseDto(
        popup.popupUuid(),
        popup.name(),
        popup.startDate(),
        popup.endDate(),
        popup.openTime(),
        popup.closeTime(),
        popup.address(),
        popup.roadAddress(),
        popup.region(),
        popup.latitude(),
        popup.longitude(),
        popup.instaPostId(),
        popup.instaPostUrl(),
        popup.captionSummary(),
        popup.imageUrlList(),
        popup.mediaType(),
        popup.recommendList(),
        popup.favoriteCount(),
        popup.viewCount(),
        popup.favorited(),
        isRead);
  }
}
