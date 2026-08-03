package com.poppang.be.domain.recommend.dto.v2;

import com.poppang.be.domain.recommend.entity.Recommend;

public record V2RecommendResponseDto(Long id, String recommendName) {

  public static V2RecommendResponseDto from(Recommend recommend) {
    return new V2RecommendResponseDto(recommend.getId(), recommend.getRecommendName());
  }
}
