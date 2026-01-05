package com.poppang.be.domain.alert.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.poppang.be.domain.popup.dto.app.response.PopupUserResponseDto;
import com.poppang.be.domain.popup.entity.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserAlertResponseDto {

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

  @JsonProperty("isFavorited")
  private boolean favorited;

  @JsonProperty("isRead")
  private boolean read;

  @Builder
  public UserAlertResponseDto(
      String popupUuid,
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
      long viewCount,
      boolean favorited,
      boolean read) {
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
    this.favorited = favorited;
    this.read = read;
  }

  public static UserAlertResponseDto from(PopupUserResponseDto dto, boolean isRead) {
    return UserAlertResponseDto.builder()
        .popupUuid(dto.getPopupUuid())
        .name(dto.getName())
        .startDate(dto.getStartDate())
        .endDate(dto.getEndDate())
        .openTime(dto.getOpenTime())
        .closeTime(dto.getCloseTime())
        .address(dto.getAddress())
        .roadAddress(dto.getRoadAddress())
        .region(dto.getRegion())
        .latitude(dto.getLatitude())
        .longitude(dto.getLongitude())
        .instaPostId(dto.getInstaPostId())
        .instaPostUrl(dto.getInstaPostUrl())
        .captionSummary(dto.getCaptionSummary())
        .imageUrlList(dto.getImageUrlList())
        .mediaType(dto.getMediaType())
        .recommendList(dto.getRecommendList())
        .favoriteCount(dto.getFavoriteCount())
        .viewCount(dto.getViewCount())
        .favorited(dto.isFavorited())
        .read(isRead)
        .build();
  }
}
