package com.poppang.be.domain.popup.dto.request;

import lombok.Getter;

@Getter
public class PopupImageUpsertRequestDto {

  private String imageUrl;
  private Integer sortOrder;
}
