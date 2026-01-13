package com.poppang.be.domain.recommend.application;

import com.poppang.be.domain.recommend.dto.response.RecommendFeaturedResponseDto;
import com.poppang.be.domain.recommend.dto.response.RecommendResponseDto;
import java.util.List;

public interface RecommendService {

  List<RecommendResponseDto> getAllRecommendList();

  List<RecommendResponseDto> webGetAllRecommendList();

  List<RecommendFeaturedResponseDto> getFeaturedForMap();
}
