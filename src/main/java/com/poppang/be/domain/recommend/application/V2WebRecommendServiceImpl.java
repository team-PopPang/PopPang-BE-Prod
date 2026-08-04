package com.poppang.be.domain.recommend.application;

import com.poppang.be.domain.recommend.dto.v2.V2WebRecommendResponseDto;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2WebRecommendServiceImpl implements V2WebRecommendService {

  private final RecommendRepository recommendRepository;

  @Override
  @Transactional(readOnly = true)
  public List<V2WebRecommendResponseDto> getAllRecommendList() {
    return recommendRepository.findAll().stream()
        .map(
            recommend ->
                new V2WebRecommendResponseDto(recommend.getId(), recommend.getRecommendName()))
        .toList();
  }
}
