package com.poppang.be.domain.recommend.dto.response;

import com.poppang.be.domain.recommend.entity.Recommend;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecommendResponseDto {

  private Long id;
  private String recommendName;

  @Builder
  public RecommendResponseDto(Long id, String recommendName) {
    this.id = id;
    this.recommendName = recommendName;
  }

  public static RecommendResponseDto from(Recommend recommend) {
    return RecommendResponseDto.builder()
        .id(recommend.getId())
        .recommendName(recommend.getRecommendName())
        .build();
  }
}
