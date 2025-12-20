package com.poppang.be.domain.users.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateAlertStatusRequestDto {

  @JsonProperty("isAlerted")
  private boolean alerted;
}
