package com.poppang.be.domain.favorite.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FavoriteCountResponseDto {

  private long count;

  @Builder
  public FavoriteCountResponseDto(long count) {
    this.count = count;
  }

  public static FavoriteCountResponseDto from(long count) {
    return FavoriteCountResponseDto.builder().count(count).build();
  }
}
