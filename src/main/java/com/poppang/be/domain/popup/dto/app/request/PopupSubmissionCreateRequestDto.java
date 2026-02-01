package com.poppang.be.domain.popup.dto.app.request;

import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class PopupSubmissionCreateRequestDto {

  private String name;
  private LocalDate startDate;
  private LocalDate endDate;
  private String address;
  private String description;
  private Long submitterUserId;

  public PopupSubmission toEntity() {
    return PopupSubmission.builder()
        .name(this.name)
        .startDate(this.startDate)
        .endDate(this.endDate)
        .address(this.address)
        .description(this.description)
        .submitterUserId(this.submitterUserId)
        .status(PopupSubmissionStatus.PENDING)
        .build();
  }
}
