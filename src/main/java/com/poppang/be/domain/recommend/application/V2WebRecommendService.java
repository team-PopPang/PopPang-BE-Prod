package com.poppang.be.domain.recommend.application;

import com.poppang.be.domain.recommend.dto.v2.V2WebRecommendResponseDto;
import java.util.List;

public interface V2WebRecommendService {

  List<V2WebRecommendResponseDto> getAllRecommendList();
}
