package com.poppang.be.domain.recommend.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.poppang.be.domain.recommend.dto.v2.V2WebRecommendResponseDto;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2WebRecommendServiceImplTest {

  @Mock private RecommendRepository recommendRepository;
  @InjectMocks private V2WebRecommendServiceImpl service;

  @Test
  void returnsAllRecommendationsInRepositoryOrderWithWebFields() {
    Recommend first = recommend(1L, "전시");
    Recommend second = recommend(2L, "패션");
    given(recommendRepository.findAll()).willReturn(List.of(first, second));

    assertThat(service.getAllRecommendList())
        .containsExactly(
            new V2WebRecommendResponseDto(1L, "전시"), new V2WebRecommendResponseDto(2L, "패션"));
    verify(recommendRepository).findAll();
  }

  private Recommend recommend(Long id, String name) {
    Recommend recommend = mock(Recommend.class);
    given(recommend.getId()).willReturn(id);
    given(recommend.getRecommendName()).willReturn(name);
    return recommend;
  }
}
