package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.v2.internal.V2WorkerPopupImageUpsertRequestDto;
import com.poppang.be.domain.popup.dto.v2.internal.V2WorkerPopupRegisterRequestDto;
import com.poppang.be.domain.popup.entity.MediaType;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2InternalPopupServiceImplTest {

  @Mock private PopupRepository popupRepository;
  @Mock private PopupImageRepository popupImageRepository;
  @Mock private RecommendRepository recommendRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;

  @InjectMocks private V2InternalPopupServiceImpl popupService;

  @Test
  void registerPopupPreservesLegacyPopupImageAndRecommendMeaning() {
    Recommend recommend = org.mockito.Mockito.mock(Recommend.class);
    when(recommendRepository.findAllById(List.of(3L))).thenReturn(List.of(recommend));

    popupService.registerPopup(registerRequest());

    ArgumentCaptor<Popup> popupCaptor = ArgumentCaptor.forClass(Popup.class);
    verify(popupRepository).save(popupCaptor.capture());
    Popup popup = popupCaptor.getValue();
    assertThat(popup.getName()).isEqualTo("팝업");
    assertThat(popup.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    assertThat(popup.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    assertThat(popup.getOpenTime()).isEqualTo(LocalTime.of(10, 30));
    assertThat(popup.getCloseTime()).isEqualTo(LocalTime.of(20, 0));
    assertThat(popup.getMediaType()).isEqualTo(MediaType.IMAGE);
    assertThat(popup.isActivated()).isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PopupImage>> imageCaptor = ArgumentCaptor.forClass(List.class);
    verify(popupImageRepository).saveAll(imageCaptor.capture());
    assertThat(imageCaptor.getValue())
        .extracting(PopupImage::getImageUrl, PopupImage::getSortOrder)
        .containsExactly(tuple("first.jpg", 0), tuple("second.jpg", 7));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PopupRecommend>> recommendCaptor = ArgumentCaptor.forClass(List.class);
    verify(popupRecommendRepository).saveAll(recommendCaptor.capture());
    assertThat(recommendCaptor.getValue()).hasSize(1);
    assertThat(recommendCaptor.getValue().get(0).getPopup()).isSameAs(popup);
    assertThat(recommendCaptor.getValue().get(0).getRecommend()).isSameAs(recommend);
  }

  @Test
  void registerPopupKeepsLegacyInvalidRecommendIdError() {
    when(recommendRepository.findAllById(List.of(3L))).thenReturn(List.of());

    assertThatThrownBy(() -> popupService.registerPopup(registerRequest()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_RECOMMEND_ID);

    verify(popupRecommendRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void registerPopupRejectsInvalidMediaTypeAsControlledWorkerRequestError() {
    V2WorkerPopupRegisterRequestDto request = registerRequestWithMediaType("UNKNOWN");

    assertThatThrownBy(() -> popupService.registerPopup(request))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INTERNAL_POPUP_REQUEST);

    verifyNoInteractions(popupRepository);
  }

  @Test
  void registerPopupRejectsNullRequestBeforeRepositoryAccess() {
    assertThatThrownBy(() -> popupService.registerPopup(null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INTERNAL_POPUP_REQUEST);

    verifyNoInteractions(popupRepository);
  }

  @Test
  void registerPopupRejectsBlankImageUrlBeforeSavingPopup() {
    V2WorkerPopupRegisterRequestDto request =
        registerRequestWithImages(List.of(new V2WorkerPopupImageUpsertRequestDto(" ", null)));

    assertThatThrownBy(() -> popupService.registerPopup(request))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INTERNAL_POPUP_REQUEST);

    verifyNoInteractions(popupRepository, popupImageRepository);
  }

  @Test
  void registerPopupRejectsNullImageBeforeSavingPopup() {
    V2WorkerPopupRegisterRequestDto request =
        registerRequestWithImages(Collections.singletonList(null));

    assertThatThrownBy(() -> popupService.registerPopup(request))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INTERNAL_POPUP_REQUEST);

    verifyNoInteractions(popupRepository, popupImageRepository);
  }

  @Test
  void upsertImagesDeletesExistingRowsAndUsesRequestOrderAsDefaultSort() {
    Popup popup = Popup.builder().id(9L).uuid("popup-uuid").build();
    when(popupRepository.findByUuid("popup-uuid")).thenReturn(Optional.of(popup));

    popupService.upsertImages(
        "popup-uuid",
        List.of(
            new V2WorkerPopupImageUpsertRequestDto("first.jpg", null),
            new V2WorkerPopupImageUpsertRequestDto("second.jpg", 4)));

    verify(popupImageRepository).deleteByPopup_Id(9L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PopupImage>> imageCaptor = ArgumentCaptor.forClass(List.class);
    verify(popupImageRepository).saveAll(imageCaptor.capture());
    assertThat(imageCaptor.getValue())
        .extracting(PopupImage::getImageUrl, PopupImage::getSortOrder)
        .containsExactly(tuple("first.jpg", 0), tuple("second.jpg", 4));
    assertThat(imageCaptor.getValue()).allMatch(image -> image.getPopup() == popup);
  }

  @Test
  void upsertImagesKeepsLegacyPopupNotFoundError() {
    when(popupRepository.findByUuid("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> popupService.upsertImages("missing", List.of()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.POPUP_NOT_FOUND);
  }

  @Test
  void upsertImagesRejectsNullListBeforeDeletingExistingRows() {
    assertThatThrownBy(() -> popupService.upsertImages("popup-uuid", null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INTERNAL_POPUP_REQUEST);

    verifyNoInteractions(popupRepository, popupImageRepository);
  }

  @Test
  void upsertImagesRejectsInvalidEntryBeforeDeletingExistingRows() {
    assertThatThrownBy(
            () ->
                popupService.upsertImages(
                    "popup-uuid", List.of(new V2WorkerPopupImageUpsertRequestDto(" ", null))))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INTERNAL_POPUP_REQUEST);

    verifyNoInteractions(popupRepository, popupImageRepository);
  }

  private V2WorkerPopupRegisterRequestDto registerRequest() {
    return registerRequestWithMediaType("IMAGE");
  }

  private V2WorkerPopupRegisterRequestDto registerRequestWithMediaType(String mediaType) {
    return registerRequest(
        mediaType,
        List.of(
            new V2WorkerPopupImageUpsertRequestDto("first.jpg", null),
            new V2WorkerPopupImageUpsertRequestDto("second.jpg", 7)));
  }

  private V2WorkerPopupRegisterRequestDto registerRequestWithImages(
      List<V2WorkerPopupImageUpsertRequestDto> images) {
    return registerRequest("IMAGE", images);
  }

  private V2WorkerPopupRegisterRequestDto registerRequest(
      String mediaType, List<V2WorkerPopupImageUpsertRequestDto> images) {
    return new V2WorkerPopupRegisterRequestDto(
        "팝업",
        LocalDate.of(2026, 8, 10),
        LocalDate.of(2026, 8, 20),
        LocalTime.of(10, 30),
        LocalTime.of(20, 0),
        "주소",
        "도로명 주소",
        127.0,
        37.0,
        "서울",
        "검색 주소",
        "post-id",
        "https://instagram.example/post",
        "요약",
        "본문",
        mediaType,
        true,
        images,
        List.of(3L));
  }

  private org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.groups.Tuple.tuple(values);
  }
}
