package com.poppang.be.domain.popup.dto.app.response;

import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopPopupSubmissionResponseDto {

  private Long id;
  private String name;
  private LocalDate startDate;
  private LocalDate endDate;
  private String address;
  private String description;
  private PopupSubmissionStatus status;
  private LocalDateTime createdAt;

  @Builder
  public PopPopupSubmissionResponseDto(
      Long id,
      String name,
      LocalDate startDate,
      LocalDate endDate,
      String address,
      String description,
      PopupSubmissionStatus status,
      LocalDateTime createdAt) {
    this.id = id;
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.address = address;
    this.description = description;
    this.status = status;
    this.createdAt = createdAt;
  }

  public static PopPopupSubmissionResponseDto from(PopupSubmission popupSubmission) {
    return PopPopupSubmissionResponseDto.builder()
        .id(popupSubmission.getId())
        .name(popupSubmission.getName())
        .startDate(popupSubmission.getStartDate())
        .endDate(popupSubmission.getEndDate())
        .address(popupSubmission.getAddress())
        .description(popupSubmission.getDescription())
        .status(popupSubmission.getStatus())
        .createdAt(popupSubmission.getCreatedAt())
        .build();
  }
}
