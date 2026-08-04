package com.poppang.be.domain.popup.dto.v2.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class V2PopupSubmissionAdminUpdateRequestDto {

  private String status;
  private String name;
  private LocalDate startDate;
  private LocalDate endDate;
  private String roadAddress;
  private String region;
  private String address;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
  private LocalTime openTime;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
  private LocalTime closeTime;

  private Double latitude;
  private Double longitude;
  private String captionSummary;
  private String caption;
  private String mediaType;
  private String instaPostUrl;
  private String instaPostId;
  private String geocodingQuery;
  private List<V2PopupSubmissionAdminImageRequestDto> imageList;
  private List<Long> recommendIdList;
}
