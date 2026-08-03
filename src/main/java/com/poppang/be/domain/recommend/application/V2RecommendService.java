package com.poppang.be.domain.recommend.application;

import com.poppang.be.domain.recommend.dto.v2.V2RecommendFeaturedResponseDto;
import com.poppang.be.domain.recommend.dto.v2.V2RecommendResponseDto;
import java.util.List;

public interface V2RecommendService {

  List<V2RecommendResponseDto> getAllRecommendList();

  List<V2RecommendFeaturedResponseDto> getFeaturedForMap();
}
