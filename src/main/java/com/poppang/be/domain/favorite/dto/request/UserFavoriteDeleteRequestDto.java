package com.poppang.be.domain.favorite.dto.request;

import lombok.Getter;

@Getter
public class UserFavoriteDeleteRequestDto {

  private String userUuid;
  private String popupUuid;
}
