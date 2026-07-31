package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.app.response.PopupScrollResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.infrastructure.PopupAdvertisementRepository;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.mapper.PopupUserResponseDtoMapper;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class PopupUserServiceImplScrollTest {

  private static final String USER_UUID = "user-uuid";

  @Mock private PopupRepository popupRepository;
  @Mock private PopupAdvertisementRepository popupAdvertisementRepository;
  @Mock private PopupImageRepository popupImageRepository;
  @Mock private RecommendRepository recommendRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private PopupTotalViewCountRepository popupTotalViewCountRepository;
  @Mock private UsersRepository usersRepository;
  @Mock private UserRecommendRepository userRecommendRepository;
  @Mock private PopupUserResponseDtoMapper popupUserResponseDtoMapper;
  @Mock private PopupCountBoostService popupCountBoostService;
  @Mock private PopupHomeFilterService popupHomeFilterService;

  @InjectMocks private PopupUserServiceImpl popupUserService;

  @Test
  void getScrollPopupListReturnsFirstPageSummaryAndNextCursor() {
    Popup first = popup(30L, "popup-30", "첫 번째 팝업");
    Popup second = popup(20L, "popup-20", "두 번째 팝업");
    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    when(popupRepository.findByActivatedTrueAndEndDateGreaterThanEqualOrderByIdDesc(
            any(LocalDate.class), any(Pageable.class)))
        .thenReturn(new SliceImpl<>(List.of(first, second), PageRequest.of(0, 15), true));
    when(popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
            List.of(30L, 20L), 0))
        .thenReturn(
            List.of(
                PopupImage.builder()
                    .id(1L)
                    .popup(first)
                    .imageUrl("/images/first.jpg")
                    .sortOrder(0)
                    .build()));
    when(userFavoriteRepository.findPopupIdsByUserUuidAndPopupIds(USER_UUID, List.of(30L, 20L)))
        .thenReturn(List.of(20L));

    PopupScrollResponseDto response = popupUserService.getScrollPopupList(USER_UUID, null);

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo(20L);
    assertThat(response.items()).hasSize(2);
    assertThat(response.items().get(0).popupUuid()).isEqualTo("popup-30");
    assertThat(response.items().get(0).thumbnailUrl()).isEqualTo("/images/first.jpg");
    assertThat(response.items().get(0).region()).isEqualTo("서울");
    assertThat(response.items().get(0).name()).isEqualTo("첫 번째 팝업");
    assertThat(response.items().get(0).favorited()).isFalse();
    assertThat(response.items().get(1).thumbnailUrl()).isNull();
    assertThat(response.items().get(1).favorited()).isTrue();

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(popupRepository)
        .findByActivatedTrueAndEndDateGreaterThanEqualOrderByIdDesc(
            any(LocalDate.class), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(15);
    verify(popupRepository, never())
        .findByActivatedTrueAndEndDateGreaterThanEqualAndIdLessThanOrderByIdDesc(
            any(LocalDate.class), any(Long.class), any(Pageable.class));
  }

  @Test
  void getScrollPopupListUsesCursorAndOmitsNextCursorOnLastPage() {
    Popup popup = popup(10L, "popup-10", "마지막 팝업");
    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    when(popupRepository.findByActivatedTrueAndEndDateGreaterThanEqualAndIdLessThanOrderByIdDesc(
            any(LocalDate.class), eq(20L), any(Pageable.class)))
        .thenReturn(new SliceImpl<>(List.of(popup), PageRequest.of(0, 15), false));
    when(popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
            List.of(10L), 0))
        .thenReturn(List.of());
    when(userFavoriteRepository.findPopupIdsByUserUuidAndPopupIds(USER_UUID, List.of(10L)))
        .thenReturn(List.of());

    PopupScrollResponseDto response = popupUserService.getScrollPopupList(USER_UUID, 20L);

    assertThat(response.items()).hasSize(1);
    assertThat(response.hasNext()).isFalse();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void getScrollPopupListReturnsEmptyPageWithoutBatchLookups() {
    when(usersRepository.findByUuid(USER_UUID))
        .thenReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    when(popupRepository.findByActivatedTrueAndEndDateGreaterThanEqualOrderByIdDesc(
            any(LocalDate.class), any(Pageable.class)))
        .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 15), false));

    PopupScrollResponseDto response = popupUserService.getScrollPopupList(USER_UUID, null);

    assertThat(response.items()).isEmpty();
    assertThat(response.nextCursor()).isNull();
    assertThat(response.hasNext()).isFalse();
    verifyNoInteractions(popupImageRepository, userFavoriteRepository);
  }

  @Test
  void getScrollPopupListRejectsUnknownUser() {
    when(usersRepository.findByUuid(USER_UUID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> popupUserService.getScrollPopupList(USER_UUID, null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    verifyNoInteractions(popupRepository, popupImageRepository, userFavoriteRepository);
  }

  private Popup popup(Long id, String popupUuid, String name) {
    return Popup.builder()
        .id(id)
        .uuid(popupUuid)
        .name(name)
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 15))
        .address("서울시 성동구")
        .region("서울")
        .captionSummary("팝업 설명")
        .caption("팝업 전체 설명")
        .activated(true)
        .build();
  }
}
