package com.poppang.be.domain.recommend.application;

import com.poppang.be.domain.recommend.dto.v2.V2RecommendFeaturedResponseDto;
import com.poppang.be.domain.recommend.dto.v2.V2RecommendResponseDto;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class V2RecommendServiceImpl implements V2RecommendService {

  private static final List<Integer> FEATURED_RECOMMEND_IDS = List.of(21);

  private final RecommendRepository recommendRepository;

  @Override
  public List<V2RecommendResponseDto> getAllRecommendList() {
    return recommendRepository.findAll().stream().map(V2RecommendResponseDto::from).toList();
  }

  @Override
  public List<V2RecommendFeaturedResponseDto> getFeaturedForMap() {
    return recommendRepository.findAllByIdIn(FEATURED_RECOMMEND_IDS).stream()
        .map(V2RecommendFeaturedResponseDto::from)
        .toList();
  }
}
