package com.poppang.be.domain.favorite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.application.PopupCountBoostValue;
import com.poppang.be.domain.popup.entity.MediaType;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2FavoritePopupResponseDtoMapperTest {

  @Mock private PopupImageRepository popupImageRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private PopupTotalViewCountRepository popupTotalViewCountRepository;
  @Mock private PopupCountBoostService popupCountBoostService;
  @Mock private Recommend recommend;
  @Mock private UserFavoriteRepository.FavoriteCountRow favoriteCountRow;
  @Mock private PopupTotalViewCountRepository.ViewCountProjection viewCountProjection;

  @InjectMocks private V2FavoritePopupResponseDtoMapper mapper;

  @Test
  void mapsTheLegacyPopupFieldsAndIncludesCountBoostsAndFavoriteState() {
    Popup popup = popup();
    PopupImage image =
        PopupImage.builder().id(11L).popup(popup).imageUrl("image.jpg").sortOrder(0).build();
    PopupRecommend popupRecommend =
        PopupRecommend.builder().popup(popup).recommend(recommend).build();
    given(recommend.getRecommendName()).willReturn("데이트");
    given(popupImageRepository.findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(List.of(1L)))
        .willReturn(List.of(image));
    given(popupRecommendRepository.findAllByPopupIdsWithRecommend(List.of(1L)))
        .willReturn(List.of(popupRecommend));
    given(userFavoriteRepository.countAllByPopupIds(List.of(1L)))
        .willReturn(List.of(favoriteCountRow));
    given(favoriteCountRow.getPopupId()).willReturn(1L);
    given(favoriteCountRow.getCnt()).willReturn(4L);
    given(popupTotalViewCountRepository.findAllViewCounts(List.of("popup-uuid")))
        .willReturn(List.of(viewCountProjection));
    given(viewCountProjection.getPopupUuid()).willReturn("popup-uuid");
    given(viewCountProjection.getViewCount()).willReturn(10L);
    given(popupCountBoostService.getBoostValueMap(List.of(1L)))
        .willReturn(Map.of(1L, new PopupCountBoostValue(3L, 2L)));

    List<V2FavoritePopupResponseDto> responses = mapper.toResponseList(List.of(popup), Set.of(1L));

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0))
        .satisfies(
            response -> {
              assertThat(response.popupUuid()).isEqualTo("popup-uuid");
              assertThat(response.name()).isEqualTo("팝업");
              assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
              assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 7, 31));
              assertThat(response.openTime()).isEqualTo(LocalTime.of(10, 30));
              assertThat(response.closeTime()).isEqualTo(LocalTime.of(20, 0));
              assertThat(response.address()).isEqualTo("주소");
              assertThat(response.roadAddress()).isEqualTo("도로명");
              assertThat(response.region()).isEqualTo("서울");
              assertThat(response.latitude()).isEqualTo(37.5);
              assertThat(response.longitude()).isEqualTo(127.0);
              assertThat(response.instaPostId()).isEqualTo("post-id");
              assertThat(response.instaPostUrl()).isEqualTo("https://instagram.example/post");
              assertThat(response.captionSummary()).isEqualTo("요약");
              assertThat(response.imageUrlList()).containsExactly("image.jpg");
              assertThat(response.mediaType()).isEqualTo(MediaType.IMAGE);
              assertThat(response.recommendList()).containsExactly("데이트");
              assertThat(response.favoriteCount()).isEqualTo(6L);
              assertThat(response.viewCount()).isEqualTo(13L);
              assertThat(response.favorited()).isTrue();
            });
  }

  @Test
  void emptyInputDoesNotLoadAnyRelatedData() {
    assertThat(mapper.toResponseList(List.of(), Set.of())).isEmpty();
    verifyNoInteractions(
        popupImageRepository,
        popupRecommendRepository,
        userFavoriteRepository,
        popupTotalViewCountRepository,
        popupCountBoostService);
  }

  private Popup popup() {
    return Popup.builder()
        .id(1L)
        .uuid("popup-uuid")
        .name("팝업")
        .startDate(LocalDate.of(2026, 7, 1))
        .endDate(LocalDate.of(2026, 7, 31))
        .openTime(LocalTime.of(10, 30))
        .closeTime(LocalTime.of(20, 0))
        .address("주소")
        .roadAddress("도로명")
        .region("서울")
        .latitude(37.5)
        .longitude(127.0)
        .instaPostId("post-id")
        .instaPostUrl("https://instagram.example/post")
        .captionSummary("요약")
        .mediaType(MediaType.IMAGE)
        .activated(true)
        .build();
  }
}
