package com.poppang.be.domain.popup.dto.app.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupSubmissionAdminImageRequestDto {

  private String sourceType;
  private String imageUrl;
  private Integer fileIndex;
  private Integer sortOrder;
}
