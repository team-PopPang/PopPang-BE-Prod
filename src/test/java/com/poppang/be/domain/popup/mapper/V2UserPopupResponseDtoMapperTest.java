package com.poppang.be.domain.popup.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.application.PopupCountBoostValue;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2UserPopupResponseDtoMapperTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private PopupImageRepository popupImageRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private PopupTotalViewCountRepository popupTotalViewCountRepository;
  @Mock private PopupCountBoostService popupCountBoostService;

  @InjectMocks private V2UserPopupResponseDtoMapper mapper;

  @Test
  void listMappingPreservesLegacyFieldsBoostsAndFavoriteState() {
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

    List<V2UserPopupResponseDto> result = mapper.toResponseDtoList(List.of(popup), Set.of(1L));

    assertThat(result).singleElement().isEqualTo(expected(14L, 12L, List.of("패션"), true));
  }

  @Test
  void detailMappingPreservesLegacyAssemblyAndChecksFavoriteForPrincipalUser() {
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
    given(userFavoriteRepository.existsByUser_UuidAndPopup_Uuid(USER_UUID, POPUP_UUID))
        .willReturn(true);

    V2UserPopupResponseDto result = mapper.toDetailResponseDto(popup, USER_UUID);

    assertThat(result).isEqualTo(expected(14L, 7L, List.of("패션"), true));
  }

  @Test
  void emptyListDoesNotLoadSupportingData() {
    assertThat(mapper.toResponseDtoList(List.of(), Set.of())).isEmpty();
    verifyNoInteractions(
        popupImageRepository,
        popupRecommendRepository,
        userFavoriteRepository,
        popupTotalViewCountRepository,
        popupCountBoostService);
  }

  @Test
  void scrollMappingKeepsTheLegacySummaryFavoriteAndNextCursorContract() {
    Popup first = popup(30L, "popup-30", "첫 팝업");
    Popup second = popup(20L, "popup-20", "두 번째 팝업");
    PopupImage thumbnail =
        PopupImage.builder()
            .id(11L)
            .popup(first)
            .imageUrl("/images/thumbnail.jpg")
            .sortOrder(0)
            .build();
    given(
            popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
                List.of(30L, 20L), 0))
        .willReturn(List.of(thumbnail));
    given(userFavoriteRepository.findPopupIdsByUserUuidAndPopupIds(USER_UUID, List.of(30L, 20L)))
        .willReturn(List.of(20L));

    V2UserPopupScrollResponseDto result =
        mapper.toScrollResponseDto(List.of(first, second), USER_UUID, true);

    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(20L);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).popupUuid()).isEqualTo("popup-30");
    assertThat(result.items().get(0).thumbnailUrl()).isEqualTo("/images/thumbnail.jpg");
    assertThat(result.items().get(0).region()).isEqualTo("서울");
    assertThat(result.items().get(0).name()).isEqualTo("첫 팝업");
    assertThat(result.items().get(0).startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(result.items().get(0).endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(result.items().get(0).favorited()).isFalse();
    assertThat(result.items().get(1).thumbnailUrl()).isNull();
    assertThat(result.items().get(1).favorited()).isTrue();
  }

  @Test
  void scrollMappingReturnsTheLegacyEmptyAndLastPageContracts() {
    Popup popup = popup(10L, "popup-10", "마지막 팝업");
    given(
            popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
                List.of(10L), 0))
        .willReturn(List.of());
    given(userFavoriteRepository.findPopupIdsByUserUuidAndPopupIds(USER_UUID, List.of(10L)))
        .willReturn(List.of());

    V2UserPopupScrollResponseDto lastPage =
        mapper.toScrollResponseDto(List.of(popup), USER_UUID, false);
    V2UserPopupScrollResponseDto empty = mapper.toScrollResponseDto(List.of(), USER_UUID, false);

    assertThat(lastPage.hasNext()).isFalse();
    assertThat(lastPage.nextCursor()).isNull();
    assertThat(empty.items()).isEmpty();
    assertThat(empty.nextCursor()).isNull();
    assertThat(empty.hasNext()).isFalse();
  }

  private Popup popup() {
    return popup(1L, POPUP_UUID, "팝업");
  }

  private Popup popup(Long id, String popupUuid, String name) {
    return Popup.builder()
        .id(id)
        .uuid(popupUuid)
        .name(name)
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 31))
        .address("주소")
        .roadAddress("서울 성동구")
        .region("서울")
        .captionSummary("요약")
        .build();
  }

  private V2UserPopupResponseDto expected(
      long favoriteCount, long viewCount, List<String> recommendList, boolean favorited) {
    return new V2UserPopupResponseDto(
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
        viewCount,
        favorited);
  }
}
