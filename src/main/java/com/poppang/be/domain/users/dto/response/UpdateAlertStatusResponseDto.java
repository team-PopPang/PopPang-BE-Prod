package com.poppang.be.domain.users.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateAlertStatusResponseDto {

  private String userUuid;

  @JsonProperty("isAlerted")
  private boolean alerted;

  @Builder
  public UpdateAlertStatusResponseDto(String userUuid, boolean alerted) {
    this.userUuid = userUuid;
    this.alerted = alerted;
  }
}
