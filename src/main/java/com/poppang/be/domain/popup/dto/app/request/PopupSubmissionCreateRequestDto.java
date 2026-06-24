package com.poppang.be.domain.popup.dto.app.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class PopupSubmissionCreateRequestDto {

  private String userUuid;
  private String name;
  private LocalDate startDate;
  private LocalDate endDate;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
  private LocalTime openTime;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
  private LocalTime closeTime;

  private String address;
  private String roadAddress;
  private String region;
  private String instaPostUrl;
  private String description;
  private List<PopupSubmissionImageRequestDto> imageList;
  private List<Long> recommendIdList;

  public PopupSubmission toEntity() {
    return PopupSubmission.builder()
        .name(this.name)
        .startDate(this.startDate)
        .endDate(this.endDate)
        .openTime(this.openTime)
        .closeTime(this.closeTime)
        .address(this.address)
        .roadAddress(this.roadAddress)
        .region(this.region)
        .instaPostUrl(this.instaPostUrl)
        .description(this.description)
        .submitterUserUuid(this.userUuid)
        .status(PopupSubmissionStatus.PENDING)
        .build();
  }
}
