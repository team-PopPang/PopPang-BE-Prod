package com.poppang.be.domain.popup.dto.v2.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class V2PopupSubmissionAdminUpdateResponseDto {

  private String popupUuid;

  @Builder
  public V2PopupSubmissionAdminUpdateResponseDto(String popupUuid) {
    this.popupUuid = popupUuid;
  }

  public static V2PopupSubmissionAdminUpdateResponseDto from(String popupUuid) {
    return V2PopupSubmissionAdminUpdateResponseDto.builder().popupUuid(popupUuid).build();
  }
}
