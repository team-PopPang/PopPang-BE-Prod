package com.poppang.be.domain.recommend.dto.response;

import com.poppang.be.domain.recommend.entity.Recommend;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecommendFeaturedResponseDto {
  private Long id;
  private String recommendName;

  @Builder
  public RecommendFeaturedResponseDto(Long id, String recommendName) {
    this.id = id;
    this.recommendName = recommendName;
  }

  public static RecommendFeaturedResponseDto from(Recommend recommend) {
    return RecommendFeaturedResponseDto.builder()
        .id(recommend.getId())
        .recommendName(recommend.getRecommendName())
        .build();
  }
}
