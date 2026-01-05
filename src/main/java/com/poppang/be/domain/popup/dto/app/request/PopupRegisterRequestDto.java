package com.poppang.be.domain.popup.dto.app.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupRegisterRequestDto {

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
  private Double longitude;
  private Double latitude;
  private String region;
  private String geocodingQuery;
  private String instaPostId;
  private String instaPostUrl;
  private String captionSummary;
  private String caption;
  private String mediaType;
  private Boolean isActive;

  private List<PopupImageUpsertRequestDto> imageList;
  private List<Long> recommendIdList;
}
