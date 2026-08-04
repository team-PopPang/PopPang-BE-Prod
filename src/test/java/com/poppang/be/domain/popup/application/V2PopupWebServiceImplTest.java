package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebFavoriteRow;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebInProgressRow;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebRandomRow;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebSearchRow;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebUpcomingRow;
import com.poppang.be.domain.popup.mapper.V2PopupWebInProgressResponseDtoMapper;
import com.poppang.be.domain.recommend.entity.Recommend;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2PopupWebServiceImplTest {

  @Mock private PopupRepository popupRepository;
  @Mock private PopupImageRepository popupImageRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private PopupTotalViewCountRepository popupTotalViewCountRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private PopupCountBoostService popupCountBoostService;
  @Mock private PopupHomeFilterService popupHomeFilterService;
  @Mock private V2PopupWebInProgressResponseDtoMapper inProgressResponseDtoMapper;

  @InjectMocks private V2PopupWebServiceImpl service;

  @Test
  void randomUsesTheExistingRepositoryLimitAndMapsProjection() {
    PopupWebRandomRow row = mock(PopupWebRandomRow.class);
    given(row.getPopupUuid()).willReturn("popup-random");
    given(row.getPopupName()).willReturn("랜덤 팝업");
    given(row.getThumbnailUrl()).willReturn("random.jpg");
    given(popupRepository.findRandomActiveWithThumbnail(5)).willReturn(List.of(row));

    var result = service.getRandomPopupList();

    assertThat(result)
        .containsExactly(
            new com.poppang.be.domain.popup.dto.v2.web.V2PopupWebRandomResponseDto(
                "popup-random", "랜덤 팝업", "random.jpg"));
    verify(popupRepository).findRandomActiveWithThumbnail(5);
  }

  @Test
  void favoriteKeepsTheLegacyViewedRepositoryAndLimit() {
    PopupWebFavoriteRow row = mock(PopupWebFavoriteRow.class);
    given(row.getPopupUuid()).willReturn("popup-popular");
    given(row.getPopupName()).willReturn("인기 팝업");
    given(row.getThumbnailUrl()).willReturn("popular.jpg");
    given(row.getRegion()).willReturn("서울");
    given(row.getStartDate()).willReturn(LocalDate.of(2026, 8, 1));
    given(row.getEndDate()).willReturn(LocalDate.of(2026, 8, 31));
    given(popupRepository.findTopViewedActiveWithThumbnail(5)).willReturn(List.of(row));

    var result = service.getFavoritePopupList();

    assertThat(result.get(0).popupUuid()).isEqualTo("popup-popular");
    assertThat(result.get(0).region()).isEqualTo("서울");
    verify(popupRepository).findTopViewedActiveWithThumbnail(5);
  }

  @Test
  void inProgressWithoutParametersUsesTheLegacyProjectionRepository() {
    PopupWebInProgressRow row = mock(PopupWebInProgressRow.class);
    given(row.getPopupUuid()).willReturn("popup-current");
    given(row.getPopupName()).willReturn("진행 팝업");
    given(row.getThumbnailUrl()).willReturn("current.jpg");
    given(row.getRegion()).willReturn("서울");
    given(row.getStartDate()).willReturn(LocalDate.of(2026, 8, 1));
    given(row.getEndDate()).willReturn(LocalDate.of(2026, 8, 31));
    given(popupRepository.findInProgressActiveWithThumbnail()).willReturn(List.of(row));

    var result = service.getInProgressPopupList(null, null, null);

    assertThat(result)
        .containsExactly(
            new V2PopupWebInProgressResponseDto(
                "popup-current",
                "진행 팝업",
                "current.jpg",
                "서울",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)));
    verifyNoInteractions(popupHomeFilterService, inProgressResponseDtoMapper);
  }

  @Test
  void inProgressFilteredRequestDefaultsBlankSortToClosingSoon() {
    Popup popup = Popup.builder().id(1L).uuid("popup-filtered").build();
    var response =
        new V2PopupWebInProgressResponseDto("popup-filtered", null, null, null, null, null);
    given(popupHomeFilterService.getFilteredPopupList("서울", "성동구", HomeSortStandard.CLOSING_SOON))
        .willReturn(List.of(popup));
    given(inProgressResponseDtoMapper.toResponseDtoList(List.of(popup)))
        .willReturn(List.of(response));

    assertThat(service.getInProgressPopupList("서울", "성동구", " ")).containsExactly(response);
  }

  @Test
  void inProgressRejectsDistrictWithoutRegion() {
    assertThatThrownBy(() -> service.getInProgressPopupList(null, "성동구", null))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.REGION_REQUIRED_FOR_DISTRICT));
    verifyNoInteractions(popupHomeFilterService);
  }

  @Test
  void inProgressRejectsInvalidSort() {
    assertThatThrownBy(() -> service.getInProgressPopupList("서울", null, "UNKNOWN"))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SORT_STANDARD));
    verifyNoInteractions(popupHomeFilterService);
  }

  @Test
  void upcomingUsesTomorrowThroughTheLegacyTenDayRangeAndCalculatesDDay() {
    LocalDate today = LocalDate.now();
    PopupWebUpcomingRow row = mock(PopupWebUpcomingRow.class);
    given(row.getPopupUuid()).willReturn("popup-upcoming");
    given(row.getPopupName()).willReturn("예정 팝업");
    given(row.getThumbnailUrl()).willReturn("upcoming.jpg");
    given(row.getRegion()).willReturn("서울");
    given(row.getStartDate()).willReturn(today.plusDays(3));
    given(row.getEndDate()).willReturn(today.plusDays(20));
    given(popupRepository.findUpcomingActiveWithThumbnail(today.plusDays(1), today.plusDays(11), 5))
        .willReturn(List.of(row));

    var result = service.getUpcomingPopupList();

    assertThat(result.get(0).dDay()).isEqualTo(3);
    assertThat(result.get(0).popupUuid()).isEqualTo("popup-upcoming");
  }

  @Test
  void searchTrimsTheQueryAndMapsTheExistingProjection() {
    PopupWebSearchRow row = mock(PopupWebSearchRow.class);
    given(row.getPopupUuid()).willReturn("popup-search");
    given(row.getPopupName()).willReturn("성수 팝업");
    given(popupRepository.searchWebActiveWithThumbnail("성수")).willReturn(List.of(row));

    var result = service.getSearchPopupList("  성수  ");

    assertThat(result.get(0).popupUuid()).isEqualTo("popup-search");
    verify(popupRepository).searchWebActiveWithThumbnail("성수");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "  \t  "})
  void searchRejectsMissingOrBlankQuery(String query) {
    assertThatThrownBy(() -> service.getSearchPopupList(query))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_POPUP_SEARCH_QUERY));
    verifyNoInteractions(popupRepository);
  }

  @Test
  void detailRejectsUnknownPopup() {
    given(popupRepository.findByUuid("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPopupDetail("missing"))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POPUP_NOT_FOUND));
  }

  @Test
  void detailCombinesSortedImagesRecommendationsRealCountsAndBoosts() {
    Popup popup =
        Popup.builder()
            .id(7L)
            .uuid("popup-detail")
            .name("상세 팝업")
            .startDate(LocalDate.of(2026, 8, 1))
            .endDate(LocalDate.of(2026, 8, 31))
            .openTime(LocalTime.of(10, 30))
            .closeTime(LocalTime.of(20, 0))
            .address("지번")
            .roadAddress("도로명")
            .region("서울")
            .instaPostUrl("https://instagram.example")
            .captionSummary("요약")
            .build();
    PopupImage first = PopupImage.builder().popup(popup).imageUrl("first.jpg").sortOrder(0).build();
    PopupImage second =
        PopupImage.builder().popup(popup).imageUrl("second.jpg").sortOrder(1).build();
    Recommend recommend = mock(Recommend.class);
    given(recommend.getRecommendName()).willReturn("캐릭터");
    PopupRecommend popupRecommend =
        PopupRecommend.builder().popup(popup).recommend(recommend).build();
    given(popupRepository.findByUuid("popup-detail")).willReturn(Optional.of(popup));
    given(popupImageRepository.findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(7L))
        .willReturn(List.of(first, second));
    given(popupRecommendRepository.findAllByPopup_Id(7L)).willReturn(List.of(popupRecommend));
    given(userFavoriteRepository.countByPopupUuid("popup-detail")).willReturn(4L);
    given(popupTotalViewCountRepository.getViewCountByPopupUuid("popup-detail")).willReturn(null);
    given(popupCountBoostService.getBoostValue(7L)).willReturn(new PopupCountBoostValue(6L, 3L));

    var result = service.getPopupDetail("popup-detail");

    assertThat(result.imageUrlList()).containsExactly("first.jpg", "second.jpg");
    assertThat(result.recommendList()).containsExactly("캐릭터");
    assertThat(result.favoriteCount()).isEqualTo(7L);
    assertThat(result.viewCount()).isEqualTo(6L);
    assertThat(result.openTime()).isEqualTo(LocalTime.of(10, 30));
  }
}
