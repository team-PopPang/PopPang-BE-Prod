package com.poppang.be.domain.users.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NicknameDuplicateResponseDto {

  @JsonProperty("isDuplicated")
  private boolean duplicated;

  @Builder
  public NicknameDuplicateResponseDto(boolean duplicated) {

    this.duplicated = duplicated;
  }

  public static NicknameDuplicateResponseDto from(boolean duplicated) {
    return NicknameDuplicateResponseDto.builder().duplicated(duplicated).build();
  }
}
