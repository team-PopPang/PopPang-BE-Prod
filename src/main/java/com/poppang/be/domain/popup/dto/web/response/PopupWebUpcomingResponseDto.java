package com.poppang.be.domain.popup.dto.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupWebUpcomingResponseDto {

  private String popupUuid;
  private String name;
  private String thumbnailUrl;
  private String region;
  private LocalDate startDate;
  private LocalDate endDate;

  private int dDay;

  @JsonProperty("dDay")
  public int getDDay() {
    return dDay;
  }

  @Builder
  public PopupWebUpcomingResponseDto(
      String popupUuid,
      String name,
      String thumbnailUrl,
      String region,
      LocalDate startDate,
      LocalDate endDate,
      int dDay) {
    this.popupUuid = popupUuid;
    this.name = name;
    this.thumbnailUrl = thumbnailUrl;
    this.region = region;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dDay = dDay;
  }
}
