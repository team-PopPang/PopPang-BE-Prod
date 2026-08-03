package com.poppang.be.domain.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.poppang.be.domain.recommend.dto.v2.V2RecommendFeaturedResponseDto;
import com.poppang.be.domain.recommend.dto.v2.V2RecommendResponseDto;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2RecommendServiceImplTest {

  @Mock private RecommendRepository recommendRepository;

  private V2RecommendServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new V2RecommendServiceImpl(recommendRepository);
  }

  @Test
  void allRecommendationsKeepRepositoryOrderAndTheLegacyJsonFields() {
    Recommend first = recommend(1L, "전시");
    Recommend second = recommend(2L, "패션");
    given(recommendRepository.findAll()).willReturn(List.of(first, second));

    List<V2RecommendResponseDto> result = service.getAllRecommendList();

    assertThat(result)
        .containsExactly(
            new V2RecommendResponseDto(1L, "전시"), new V2RecommendResponseDto(2L, "패션"));
  }

  @Test
  void featuredRecommendationsKeepTheLegacyServerManagedId() {
    Recommend featured = recommend(21L, "캐릭터");
    given(recommendRepository.findAllByIdIn(List.of(21))).willReturn(List.of(featured));

    List<V2RecommendFeaturedResponseDto> result = service.getFeaturedForMap();

    assertThat(result).containsExactly(new V2RecommendFeaturedResponseDto(21L, "캐릭터"));
    verify(recommendRepository).findAllByIdIn(List.of(21));
  }

  private Recommend recommend(Long id, String name) {
    Recommend recommend = mock(Recommend.class);
    given(recommend.getId()).willReturn(id);
    given(recommend.getRecommendName()).willReturn(name);
    return recommend;
  }
}
