package com.poppang.be.domain.popup.dto.app.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupTotalViewCountResponseDto {

  private long totalViewCount;

  @Builder
  public PopupTotalViewCountResponseDto(long totalViewCount) {
    this.totalViewCount = totalViewCount;
  }
}
