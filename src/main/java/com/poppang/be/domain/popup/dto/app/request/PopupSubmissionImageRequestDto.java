package com.poppang.be.domain.popup.dto.app.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupSubmissionImageRequestDto {

  private String imageUrl;
  private Integer sortOrder;
}
