package com.poppang.be.domain.popup.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.application.PopupCountBoostValue;
import com.poppang.be.domain.popup.dto.v2.V2PopupResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2PopupResponseDtoMapperTest {

  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private PopupImageRepository popupImageRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private PopupTotalViewCountRepository popupTotalViewCountRepository;
  @Mock private PopupCountBoostService popupCountBoostService;

  @InjectMocks private V2PopupResponseDtoMapper mapper;

  @Test
  void listMappingPreservesFieldsAndAddsFavoriteAndViewBoosts() {
    Popup popup = popup();
    PopupImage image =
        PopupImage.builder().id(11L).popup(popup).imageUrl("image.jpg").sortOrder(0).build();
    Recommend recommend = mock(Recommend.class);
    given(recommend.getRecommendName()).willReturn("패션");
    PopupRecommend popupRecommend =
        PopupRecommend.builder().popup(popup).recommend(recommend).build();
    UserFavoriteRepository.FavoriteCountRow favoriteRow =
        mock(UserFavoriteRepository.FavoriteCountRow.class);
    given(favoriteRow.getPopupId()).willReturn(1L);
    given(favoriteRow.getCnt()).willReturn(3L);
    PopupTotalViewCountRepository.ViewCountProjection viewRow =
        mock(PopupTotalViewCountRepository.ViewCountProjection.class);
    given(viewRow.getPopupUuid()).willReturn(POPUP_UUID);
    given(viewRow.getViewCount()).willReturn(5L);

    given(popupImageRepository.findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(List.of(1L)))
        .willReturn(List.of(image));
    given(popupRecommendRepository.findAllByPopupIdsWithRecommend(List.of(1L)))
        .willReturn(List.of(popupRecommend));
    given(userFavoriteRepository.countAllByPopupIds(List.of(1L))).willReturn(List.of(favoriteRow));
    given(popupTotalViewCountRepository.findAllViewCounts(List.of(POPUP_UUID)))
        .willReturn(List.of(viewRow));
    given(popupCountBoostService.getBoostValueMap(List.of(1L)))
        .willReturn(Map.of(1L, new PopupCountBoostValue(7L, 11L)));

    List<V2PopupResponseDto> result = mapper.toResponseDtoList(List.of(popup));

    assertThat(result).singleElement().isEqualTo(expected(14L, 12L, List.of("패션")));
  }

  @Test
  void detailMappingPreservesLegacyDetailAssemblyAndUsesZeroForMissingViewCount() {
    Popup popup = popup();
    PopupImage image =
        PopupImage.builder().id(11L).popup(popup).imageUrl("image.jpg").sortOrder(0).build();
    Recommend recommend = mock(Recommend.class);
    given(recommend.getRecommendName()).willReturn("패션");
    PopupRecommend popupRecommend =
        PopupRecommend.builder().popup(popup).recommend(recommend).build();

    given(popupImageRepository.findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(1L))
        .willReturn(List.of(image));
    given(popupRecommendRepository.findAllByPopup_Id(1L)).willReturn(List.of(popupRecommend));
    given(userFavoriteRepository.countByPopupUuid(POPUP_UUID)).willReturn(3L);
    given(popupTotalViewCountRepository.getViewCountByPopupUuid(POPUP_UUID)).willReturn(null);
    given(popupCountBoostService.getBoostValue(1L)).willReturn(new PopupCountBoostValue(7L, 11L));

    V2PopupResponseDto result = mapper.toDetailResponseDto(popup);

    assertThat(result).isEqualTo(expected(14L, 7L, List.of("패션")));
  }

  @Test
  void emptyListDoesNotLoadSupportingData() {
    assertThat(mapper.toResponseDtoList(List.of())).isEmpty();
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
        .uuid(POPUP_UUID)
        .name("팝업")
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 31))
        .address("주소")
        .roadAddress("서울 성동구")
        .region("서울")
        .captionSummary("요약")
        .build();
  }

  private V2PopupResponseDto expected(
      long favoriteCount, long viewCount, List<String> recommendList) {
    return new V2PopupResponseDto(
        POPUP_UUID,
        "팝업",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        null,
        null,
        "주소",
        "서울 성동구",
        "서울",
        null,
        null,
        null,
        null,
        "요약",
        List.of("image.jpg"),
        null,
        recommendList,
        favoriteCount,
        viewCount);
  }
}
