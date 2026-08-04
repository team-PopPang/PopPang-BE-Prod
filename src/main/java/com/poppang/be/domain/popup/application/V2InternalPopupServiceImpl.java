package com.poppang.be.domain.popup.application;

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
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2InternalPopupServiceImpl implements V2InternalPopupService {

  private final PopupRepository popupRepository;
  private final PopupImageRepository popupImageRepository;
  private final RecommendRepository recommendRepository;
  private final PopupRecommendRepository popupRecommendRepository;

  @Override
  @Transactional
  public void registerPopup(V2WorkerPopupRegisterRequestDto request) {
    Popup popup =
        Popup.builder()
            .name(request.name())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .openTime(request.openTime())
            .closeTime(request.closeTime())
            .address(request.address())
            .roadAddress(request.roadAddress())
            .longitude(request.longitude())
            .latitude(request.latitude())
            .region(request.region())
            .geocodingQuery(request.geocodingQuery())
            .instaPostId(request.instaPostId())
            .instaPostUrl(request.instaPostUrl())
            .captionSummary(request.captionSummary())
            .caption(request.caption())
            .mediaType(request.mediaType() == null ? null : MediaType.valueOf(request.mediaType()))
            .activated(Boolean.TRUE.equals(request.isActive()))
            .build();
    popupRepository.save(popup);

    if (request.imageList() != null && !request.imageList().isEmpty()) {
      List<PopupImage> images = new ArrayList<>();
      for (int index = 0; index < request.imageList().size(); index++) {
        V2WorkerPopupImageUpsertRequestDto image = request.imageList().get(index);
        images.add(toPopupImage(popup, image, index));
      }
      popupImageRepository.saveAll(images);
    }

    if (request.recommendIdList() != null && !request.recommendIdList().isEmpty()) {
      List<Recommend> recommends = recommendRepository.findAllById(request.recommendIdList());
      if (recommends.size() != request.recommendIdList().size()) {
        throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
      }
      popupRecommendRepository.saveAll(
          recommends.stream()
              .map(recommend -> PopupRecommend.builder().popup(popup).recommend(recommend).build())
              .toList());
    }
  }

  @Override
  @Transactional
  public void upsertImages(String popupUuid, List<V2WorkerPopupImageUpsertRequestDto> images) {
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    popupImageRepository.deleteByPopup_Id(popup.getId());

    List<PopupImage> replacements = new ArrayList<>();
    for (int index = 0; index < images.size(); index++) {
      replacements.add(toPopupImage(popup, images.get(index), index));
    }
    popupImageRepository.saveAll(replacements);
  }

  private PopupImage toPopupImage(
      Popup popup, V2WorkerPopupImageUpsertRequestDto image, int defaultSortOrder) {
    return PopupImage.builder()
        .popup(popup)
        .imageUrl(image.imageUrl())
        .sortOrder(image.sortOrder() == null ? defaultSortOrder : image.sortOrder())
        .build();
  }
}
