package com.poppang.be.domain.popup.dto.app.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class PopupSubmissionAdminUpdateResponseDto {

  private String popupUuid;

  @Builder
  public PopupSubmissionAdminUpdateResponseDto(String popupUuid) {
    this.popupUuid = popupUuid;
  }

  public static PopupSubmissionAdminUpdateResponseDto from(String popupUuid) {
    return PopupSubmissionAdminUpdateResponseDto.builder().popupUuid(popupUuid).build();
  }
}
