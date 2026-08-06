package com.poppang.be.domain.popup.dto.v2.admin;

import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class V2PopupSubmissionAdminListResponseDto {

  private Long popupSubmissionId;
  private String name;
  private String roadAddress;
  private String region;
  private String submitterUserUuid;
  private String submitterNickname;
  private LocalDateTime submittedAt;
  private PopupSubmissionStatus status;

  @Builder
  public V2PopupSubmissionAdminListResponseDto(
      Long popupSubmissionId,
      String name,
      String roadAddress,
      String region,
      String submitterUserUuid,
      String submitterNickname,
      LocalDateTime submittedAt,
      PopupSubmissionStatus status) {
    this.popupSubmissionId = popupSubmissionId;
    this.name = name;
    this.roadAddress = roadAddress;
    this.region = region;
    this.submitterUserUuid = submitterUserUuid;
    this.submitterNickname = submitterNickname;
    this.submittedAt = submittedAt;
    this.status = status;
  }

  public static V2PopupSubmissionAdminListResponseDto from(
      PopupSubmission popupSubmission, String submitterNickname) {
    return V2PopupSubmissionAdminListResponseDto.builder()
        .popupSubmissionId(popupSubmission.getId())
        .name(popupSubmission.getName())
        .roadAddress(popupSubmission.getRoadAddress())
        .region(popupSubmission.getRegion())
        .submitterUserUuid(popupSubmission.getSubmitterUserUuid())
        .submitterNickname(submitterNickname)
        .submittedAt(popupSubmission.getCreatedAt())
        .status(popupSubmission.getStatus())
        .build();
  }
}
