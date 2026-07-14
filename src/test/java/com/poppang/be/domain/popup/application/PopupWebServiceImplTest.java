package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebInProgressRow;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebSearchRow;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopupWebServiceImplTest {

  @Mock private PopupRepository popupRepository;
  @Mock private PopupImageRepository popupImageRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private UsersRepository usersRepository;
  @Mock private PopupTotalViewCountRepository popupTotalViewCountRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private PopupCountBoostService popupCountBoostService;

  @InjectMocks private PopupWebServiceImpl popupWebService;

  @Test
  void getInProgressPopupListMapsEveryCardFieldAndPreservesRepositoryOrder() {
    PopupWebInProgressRow closingFirst =
        row(
            "popup-closing-first",
            "먼저 종료하는 팝업",
            "https://example.com/first.jpg",
            "서울 성동구",
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 14));
    PopupWebInProgressRow closingLater =
        row(
            "popup-closing-later",
            "나중에 종료하는 팝업",
            null,
            null,
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 7, 31));
    given(popupRepository.findInProgressActiveWithThumbnail())
        .willReturn(List.of(closingFirst, closingLater));

    List<PopupWebInProgressResponseDto> result = popupWebService.getInProgressPopupList();

    assertThat(result)
        .extracting(PopupWebInProgressResponseDto::getPopupUuid)
        .containsExactly("popup-closing-first", "popup-closing-later");
    assertThat(result.get(0))
        .satisfies(
            popup -> {
              assertThat(popup.getName()).isEqualTo("먼저 종료하는 팝업");
              assertThat(popup.getThumbnailUrl()).isEqualTo("https://example.com/first.jpg");
              assertThat(popup.getRegion()).isEqualTo("서울 성동구");
              assertThat(popup.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 10));
              assertThat(popup.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 14));
            });
    assertThat(result.get(1).getThumbnailUrl()).isNull();
    assertThat(result.get(1).getRegion()).isNull();
  }

  @Test
  void getInProgressPopupListReturnsEmptyListWhenRepositoryHasNoRows() {
    given(popupRepository.findInProgressActiveWithThumbnail()).willReturn(List.of());

    List<PopupWebInProgressResponseDto> result = popupWebService.getInProgressPopupList();

    assertThat(result).isEmpty();
  }

  @Test
  void getSearchPopupListTrimsQueryMapsEveryFieldAndPreservesRepositoryOrder() {
    PopupWebSearchRow exactName =
        searchRow(
            "popup-exact",
            "성수",
            "https://example.com/exact.jpg",
            "서울 성동구",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31));
    PopupWebSearchRow regionMatch =
        searchRow(
            "popup-region",
            "여름 브랜드 전시",
            null,
            "서울 성수",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31));
    given(popupRepository.searchWebActiveWithThumbnail("성수"))
        .willReturn(List.of(exactName, regionMatch));

    List<PopupWebSearchResponseDto> result = popupWebService.getSearchPopupList("  성수  ");

    assertThat(result)
        .extracting(PopupWebSearchResponseDto::getPopupUuid)
        .containsExactly("popup-exact", "popup-region");
    assertThat(result.get(0))
        .satisfies(
            popup -> {
              assertThat(popup.getName()).isEqualTo("성수");
              assertThat(popup.getThumbnailUrl()).isEqualTo("https://example.com/exact.jpg");
              assertThat(popup.getRegion()).isEqualTo("서울 성동구");
              assertThat(popup.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
              assertThat(popup.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
            });
    assertThat(result.get(1).getThumbnailUrl()).isNull();
    verify(popupRepository).searchWebActiveWithThumbnail("성수");
  }

  @Test
  void getSearchPopupListReturnsEmptyListWhenRepositoryHasNoRows() {
    given(popupRepository.searchWebActiveWithThumbnail("없는검색어")).willReturn(List.of());

    List<PopupWebSearchResponseDto> result = popupWebService.getSearchPopupList("없는검색어");

    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " \t "})
  void getSearchPopupListRejectsMissingOrBlankQuery(String q) {
    assertThatThrownBy(() -> popupWebService.getSearchPopupList(q))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_POPUP_SEARCH_QUERY));

    verifyNoInteractions(popupRepository);
  }

  private PopupWebInProgressRow row(
      String popupUuid,
      String popupName,
      String thumbnailUrl,
      String region,
      LocalDate startDate,
      LocalDate endDate) {
    return new TestPopupWebInProgressRow(
        popupUuid, popupName, thumbnailUrl, region, startDate, endDate);
  }

  private PopupWebSearchRow searchRow(
      String popupUuid,
      String popupName,
      String thumbnailUrl,
      String region,
      LocalDate startDate,
      LocalDate endDate) {
    return new TestPopupWebSearchRow(
        popupUuid, popupName, thumbnailUrl, region, startDate, endDate);
  }

  private record TestPopupWebInProgressRow(
      String popupUuid,
      String popupName,
      String thumbnailUrl,
      String region,
      LocalDate startDate,
      LocalDate endDate)
      implements PopupWebInProgressRow {

    @Override
    public String getPopupUuid() {
      return popupUuid;
    }

    @Override
    public String getPopupName() {
      return popupName;
    }

    @Override
    public String getThumbnailUrl() {
      return thumbnailUrl;
    }

    @Override
    public String getRegion() {
      return region;
    }

    @Override
    public LocalDate getStartDate() {
      return startDate;
    }

    @Override
    public LocalDate getEndDate() {
      return endDate;
    }
  }

  private record TestPopupWebSearchRow(
      String popupUuid,
      String popupName,
      String thumbnailUrl,
      String region,
      LocalDate startDate,
      LocalDate endDate)
      implements PopupWebSearchRow {

    @Override
    public String getPopupUuid() {
      return popupUuid;
    }

    @Override
    public String getPopupName() {
      return popupName;
    }

    @Override
    public String getThumbnailUrl() {
      return thumbnailUrl;
    }

    @Override
    public String getRegion() {
      return region;
    }

    @Override
    public LocalDate getStartDate() {
      return startDate;
    }

    @Override
    public LocalDate getEndDate() {
      return endDate;
    }
  }
}
