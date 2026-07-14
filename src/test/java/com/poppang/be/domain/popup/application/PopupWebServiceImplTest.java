package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebInProgressRow;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
