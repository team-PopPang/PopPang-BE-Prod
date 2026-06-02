package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupCountBoost;
import com.poppang.be.domain.popup.infrastructure.PopupCountBoostRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopupCountBoostBatchServiceTest {

  @Mock private PopupRepository popupRepository;

  @Mock private PopupCountBoostRepository popupCountBoostRepository;

  @InjectMocks private PopupCountBoostBatchService popupCountBoostBatchService;

  @Test
  void boostAllPopupsCreatesBoostForPopup() {
    LocalDate boostedDate = LocalDate.of(2026, 6, 3);
    Popup popup = Popup.builder().id(1L).build();

    when(popupRepository.findAll()).thenReturn(List.of(popup));
    when(popupCountBoostRepository.findAllByPopupIdIn(List.of(1L))).thenReturn(List.of());

    int boostedCount = popupCountBoostBatchService.boostAllPopups(boostedDate);

    assertThat(boostedCount).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PopupCountBoost>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(popupCountBoostRepository).saveAll(captor.capture());

    List<PopupCountBoost> savedBoostList =
        StreamSupport.stream(captor.getValue().spliterator(), false).toList();
    assertThat(savedBoostList).hasSize(1);

    PopupCountBoost savedBoost = savedBoostList.get(0);
    assertThat(savedBoost.getPopupId()).isEqualTo(1L);
    assertThat(savedBoost.getLastBoostedDate()).isEqualTo(boostedDate);
    assertThat(savedBoost.getViewCountBoost())
        .isBetween(0L, (long) PopupCountBoostBatchService.MAX_VIEW_COUNT_BOOST_DELTA);
    assertThat(savedBoost.getFavoriteCountBoost())
        .isBetween(0L, (long) PopupCountBoostBatchService.MAX_FAVORITE_COUNT_BOOST_DELTA);
  }

  @Test
  void boostAllPopupsSkipsPopupAlreadyBoostedOnSameDate() {
    LocalDate boostedDate = LocalDate.of(2026, 6, 3);
    Popup popup = Popup.builder().id(1L).build();
    PopupCountBoost existingBoost = new PopupCountBoost(popup);
    existingBoost.addBoost(5L, 2L, boostedDate);

    when(popupRepository.findAll()).thenReturn(List.of(popup));
    when(popupCountBoostRepository.findAllByPopupIdIn(List.of(1L)))
        .thenReturn(List.of(existingBoost));

    int boostedCount = popupCountBoostBatchService.boostAllPopups(boostedDate);

    assertThat(boostedCount).isZero();
    assertThat(existingBoost.getViewCountBoost()).isEqualTo(5L);
    assertThat(existingBoost.getFavoriteCountBoost()).isEqualTo(2L);
    verify(popupCountBoostRepository, never()).saveAll(any());
  }

  @Test
  void boostAllPopupsAddsToExistingBoostFromDifferentDate() {
    LocalDate previousDate = LocalDate.of(2026, 6, 2);
    LocalDate boostedDate = LocalDate.of(2026, 6, 3);
    Popup popup = Popup.builder().id(1L).build();
    PopupCountBoost existingBoost = new PopupCountBoost(popup);
    existingBoost.addBoost(5L, 2L, previousDate);

    when(popupRepository.findAll()).thenReturn(List.of(popup));
    when(popupCountBoostRepository.findAllByPopupIdIn(List.of(1L)))
        .thenReturn(List.of(existingBoost));

    int boostedCount = popupCountBoostBatchService.boostAllPopups(boostedDate);

    assertThat(boostedCount).isEqualTo(1);
    assertThat(existingBoost.getLastBoostedDate()).isEqualTo(boostedDate);
    assertThat(existingBoost.getViewCountBoost())
        .isBetween(5L, 5L + PopupCountBoostBatchService.MAX_VIEW_COUNT_BOOST_DELTA);
    assertThat(existingBoost.getFavoriteCountBoost())
        .isBetween(2L, 2L + PopupCountBoostBatchService.MAX_FAVORITE_COUNT_BOOST_DELTA);
    verify(popupCountBoostRepository, never()).saveAll(any());
  }
}
