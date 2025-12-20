package com.poppang.be.domain.recommend.application;

import com.poppang.be.domain.recommend.dto.response.RecommendResponseDto;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

  private final RecommendRepository recommendRepository;

  @Override
  public List<RecommendResponseDto> getAllRecommendList() {
    List<Recommend> recommendList = recommendRepository.findAll();
    List<RecommendResponseDto> recommendResponseDtoList = new ArrayList<>();

    for (Recommend recommend : recommendList) {
      recommendResponseDtoList.add(RecommendResponseDto.from(recommend));
    }

    return recommendResponseDtoList;
  }
}
