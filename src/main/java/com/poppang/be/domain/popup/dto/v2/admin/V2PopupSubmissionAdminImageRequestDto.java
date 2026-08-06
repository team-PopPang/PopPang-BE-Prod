package com.poppang.be.domain.popup.dto.v2.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class V2PopupSubmissionAdminImageRequestDto {

  private String sourceType;
  private String imageUrl;
  private Integer fileIndex;
  private Integer sortOrder;
}
