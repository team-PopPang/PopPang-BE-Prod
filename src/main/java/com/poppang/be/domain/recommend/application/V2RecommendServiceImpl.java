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
public class V2RecommendServiceImpl implements V2RecommendService {

  private static final List<Long> FEATURED_RECOMMEND_IDS = List.of(21L);

  private final RecommendRepository recommendRepository;

  @Override
  @Transactional(readOnly = true)
  public List<V2RecommendResponseDto> getAllRecommendList() {
    return recommendRepository.findAll().stream().map(V2RecommendResponseDto::from).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2RecommendFeaturedResponseDto> getFeaturedForMap() {
    return recommendRepository.findAllById(FEATURED_RECOMMEND_IDS).stream()
        .map(V2RecommendFeaturedResponseDto::from)
        .toList();
  }
}
