package com.poppang.be.domain.recommend.application;

import com.poppang.be.domain.recommend.dto.response.RecommendFeaturedResponseDto;
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

  private static final List<Integer> FEATURED_RECOMMEND_IDS = List.of(21);

  @Override
  public List<RecommendResponseDto> getAllRecommendList() {
    List<Recommend> recommendList = recommendRepository.findAll();
    List<RecommendResponseDto> recommendResponseDtoList = new ArrayList<>();

    for (Recommend recommend : recommendList) {
      recommendResponseDtoList.add(RecommendResponseDto.from(recommend));
    }

    return recommendResponseDtoList;
  }

  @Override
  public List<RecommendResponseDto> webGetAllRecommendList() {
    List<Recommend> recommendList = recommendRepository.findAll();
    List<RecommendResponseDto> recommendResponseDtoList = new ArrayList<>();

    for (Recommend recommend : recommendList) {
      recommendResponseDtoList.add(RecommendResponseDto.from(recommend));
    }

    return recommendResponseDtoList;
  }

  @Override
  public List<RecommendFeaturedResponseDto> getFeaturedForMap() {
    List<Recommend> recommendList = recommendRepository.findAllByIdIn(FEATURED_RECOMMEND_IDS);

    return recommendList.stream().map(RecommendFeaturedResponseDto::from).toList();
  }
}
