package com.poppang.be.domain.popup.dto.web.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupWebDetailResponseDto {

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
  private String instaPostUrl;
  private String captionSummary;

  private List<String> imageUrlList;
  private List<String> recommendList;
  private long favoriteCount;
  private long viewCount;

  @Builder
  public PopupWebDetailResponseDto(
      String popupUuid,
      String name,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime openTime,
      LocalTime closeTime,
      String address,
      String roadAddress,
      String region,
      String instaPostUrl,
      String captionSummary,
      List<String> imageUrlList,
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
    this.instaPostUrl = instaPostUrl;
    this.captionSummary = captionSummary;
    this.imageUrlList = imageUrlList;
    this.recommendList = recommendList;
    this.favoriteCount = favoriteCount;
    this.viewCount = viewCount;
  }
}
