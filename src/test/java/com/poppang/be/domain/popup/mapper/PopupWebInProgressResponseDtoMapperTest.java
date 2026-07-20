package com.poppang.be.domain.popup.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopupWebInProgressResponseDtoMapperTest {

  @Mock private PopupImageRepository popupImageRepository;

  @InjectMocks private PopupWebInProgressResponseDtoMapper mapper;

  @Test
  void toResponseDtoListUsesFirstPrimaryImageAndPreservesPopupOrderAndCardFields() {
    Popup first = popup(1L, "popup-first", "첫 팝업", "서울 성동구");
    Popup second = popup(2L, "popup-second", "둘째 팝업", "부산 해운대구");
    PopupImage firstPrimary =
        PopupImage.builder().id(11L).popup(first).imageUrl("first.jpg").sortOrder(0).build();
    PopupImage duplicatePrimary =
        PopupImage.builder().id(12L).popup(first).imageUrl("duplicate.jpg").sortOrder(0).build();
    given(
            popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
                List.of(1L, 2L), 0))
        .willReturn(List.of(firstPrimary, duplicatePrimary));

    List<PopupWebInProgressResponseDto> result = mapper.toResponseDtoList(List.of(first, second));

    assertThat(result)
        .extracting(PopupWebInProgressResponseDto::getPopupUuid)
        .containsExactly("popup-first", "popup-second");
    assertThat(result.get(0))
        .satisfies(
            popup -> {
              assertThat(popup.getName()).isEqualTo("첫 팝업");
              assertThat(popup.getThumbnailUrl()).isEqualTo("first.jpg");
              assertThat(popup.getRegion()).isEqualTo("서울 성동구");
              assertThat(popup.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
              assertThat(popup.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
            });
    assertThat(result.get(1).getThumbnailUrl()).isNull();
    verify(popupImageRepository)
        .findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(List.of(1L, 2L), 0);
  }

  @Test
  void toResponseDtoListReturnsEmptyListWithoutLoadingImages() {
    assertThat(mapper.toResponseDtoList(List.of())).isEmpty();
    verifyNoInteractions(popupImageRepository);
  }

  private Popup popup(Long id, String uuid, String name, String region) {
    return Popup.builder()
        .id(id)
        .uuid(uuid)
        .name(name)
        .region(region)
        .startDate(LocalDate.of(2026, 7, 1))
        .endDate(LocalDate.of(2026, 7, 31))
        .build();
  }
}
