package com.poppang.be.domain.popup.dto.v2;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties("imageList")
public class V2PopupSubmissionCreateRequestDto {

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
  private List<Long> recommendIdList;

  public PopupSubmission toEntity(String submitterUserUuid) {
    return PopupSubmission.builder()
        .name(name)
        .startDate(startDate)
        .endDate(endDate)
        .openTime(openTime)
        .closeTime(closeTime)
        .address(address)
        .roadAddress(roadAddress)
        .region(region)
        .instaPostUrl(instaPostUrl)
        .description(description)
        .submitterUserUuid(submitterUserUuid)
        .status(PopupSubmissionStatus.PENDING)
        .build();
  }
}
