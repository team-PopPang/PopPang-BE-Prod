package com.poppang.be.domain.popup.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
class V2PopupWebInProgressResponseDtoMapperTest {

  @Mock private PopupImageRepository popupImageRepository;
  @InjectMocks private V2PopupWebInProgressResponseDtoMapper mapper;

  @Test
  void mapsOneThumbnailPerPopupWithTheExistingBatchRepository() {
    Popup first = popup(1L, "popup-1", "첫 팝업");
    Popup second = popup(2L, "popup-2", "둘째 팝업");
    PopupImage firstImage = image(10L, first, "first.jpg");
    PopupImage duplicateImage = image(11L, first, "duplicate.jpg");
    given(
            popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
                List.of(1L, 2L), 0))
        .willReturn(List.of(firstImage, duplicateImage));

    var result = mapper.toResponseDtoList(List.of(first, second));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).thumbnailUrl()).isEqualTo("first.jpg");
    assertThat(result.get(1).thumbnailUrl()).isNull();
  }

  private Popup popup(Long id, String uuid, String name) {
    return Popup.builder()
        .id(id)
        .uuid(uuid)
        .name(name)
        .region("서울")
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 31))
        .build();
  }

  private PopupImage image(Long id, Popup popup, String imageUrl) {
    return PopupImage.builder().id(id).popup(popup).imageUrl(imageUrl).sortOrder(0).build();
  }
}
