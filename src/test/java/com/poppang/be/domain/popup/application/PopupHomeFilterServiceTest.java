package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopupHomeFilterServiceTest {

  @Mock private PopupRepository popupRepository;

  @InjectMocks private PopupHomeFilterService popupHomeFilterService;

  @ParameterizedTest
  @EnumSource(HomeSortStandard.class)
  void getFilteredPopupListNormalizesRegionAndDistrictAndUsesRequestedSort(HomeSortStandard sort) {
    Popup popup = Popup.builder().id(1L).uuid("popup-uuid").build();
    givenRepositoryResult(sort, "서울", "성동구", List.of(popup));

    List<Popup> result = popupHomeFilterService.getFilteredPopupList("서울특별시", "성동", sort);

    assertThat(result).containsExactly(popup);
    verifyRepositoryCall(sort, "서울", "성동구");
  }

  @Test
  void getFilteredPopupListTreatsWholeDistrictAsEntireRegion() {
    given(popupRepository.findActiveByClosingSoon("서울", null)).willReturn(List.of());

    List<Popup> result =
        popupHomeFilterService.getFilteredPopupList("서울", "전체", HomeSortStandard.CLOSING_SOON);

    assertThat(result).isEmpty();
    verify(popupRepository).findActiveByClosingSoon("서울", null);
  }

  @Test
  void getFilteredPopupListRejectsMissingSort() {
    assertThatThrownBy(() -> popupHomeFilterService.getFilteredPopupList("서울", "전체", null))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SORT_STANDARD));

    verifyNoInteractions(popupRepository);
  }

  private void givenRepositoryResult(
      HomeSortStandard sort, String region, String district, List<Popup> result) {
    switch (sort) {
      case NEWEST -> given(popupRepository.findActiveByNewest(region, district)).willReturn(result);
      case CLOSING_SOON -> given(popupRepository.findActiveByClosingSoon(region, district))
          .willReturn(result);
      case MOST_FAVORITED -> given(popupRepository.findActiveByMostFavorited(region, district))
          .willReturn(result);
      case MOST_VIEWED -> given(popupRepository.findActiveByMostViewed(region, district))
          .willReturn(result);
    }
  }

  private void verifyRepositoryCall(HomeSortStandard sort, String region, String district) {
    switch (sort) {
      case NEWEST -> verify(popupRepository).findActiveByNewest(region, district);
      case CLOSING_SOON -> verify(popupRepository).findActiveByClosingSoon(region, district);
      case MOST_FAVORITED -> verify(popupRepository).findActiveByMostFavorited(region, district);
      case MOST_VIEWED -> verify(popupRepository).findActiveByMostViewed(region, district);
    }
  }
}
