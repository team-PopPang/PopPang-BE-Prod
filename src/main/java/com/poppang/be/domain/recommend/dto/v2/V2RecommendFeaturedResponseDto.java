package com.poppang.be.domain.recommend.dto.v2;

import com.poppang.be.domain.recommend.entity.Recommend;

public record V2RecommendFeaturedResponseDto(Long id, String recommendName) {

  public static V2RecommendFeaturedResponseDto from(Recommend recommend) {
    return new V2RecommendFeaturedResponseDto(recommend.getId(), recommend.getRecommendName());
  }
}
