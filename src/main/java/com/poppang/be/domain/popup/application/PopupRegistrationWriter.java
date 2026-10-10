package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupImageUpsertRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupRegisterRequestDto;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PopupRegistrationWriter {
  private final PopupRepository popupRepository;
  private final PopupImageRepository popupImageRepository;
  private final RecommendRepository recommendRepository;
  private final PopupRecommendRepository popupRecommendRepository;

  // 유니크 충돌 시 팝업과 연관 데이터의 트랜잭션을 모두 종료한 뒤 기존 UUID를 조회한다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Popup register(PopupRegisterRequestDto popupRegisterRequestDto) {
    // popup 테이블 저장
    Popup popup =
        Popup.builder()
            .name(popupRegisterRequestDto.getName())
            .startDate(popupRegisterRequestDto.getStartDate())
            .endDate(popupRegisterRequestDto.getEndDate())
            .openTime(popupRegisterRequestDto.getOpenTime())
            .closeTime(popupRegisterRequestDto.getCloseTime())
            .address(popupRegisterRequestDto.getAddress())
            .roadAddress(popupRegisterRequestDto.getRoadAddress())
            .longitude(popupRegisterRequestDto.getLongitude())
            .latitude(popupRegisterRequestDto.getLatitude())
            .region(popupRegisterRequestDto.getRegion())
            .geocodingQuery(popupRegisterRequestDto.getGeocodingQuery())
            .instaPostId(popupRegisterRequestDto.getInstaPostId())
            .instaPostUrl(popupRegisterRequestDto.getInstaPostUrl())
            .captionSummary(popupRegisterRequestDto.getCaptionSummary())
            .caption(popupRegisterRequestDto.getCaption())
            .mediaType(
                popupRegisterRequestDto.getMediaType() != null
                    ? MediaType.valueOf(popupRegisterRequestDto.getMediaType())
                    : null)
            .activated(Boolean.TRUE.equals(popupRegisterRequestDto.getIsActive()))
            .build();
    popupRepository.save(popup);

    // popup 이미지 저장
    if (popupRegisterRequestDto.getImageList() != null
        && !popupRegisterRequestDto.getImageList().isEmpty()) {
      List<PopupImage> imageList = new ArrayList<>();
      for (int i = 0; i < popupRegisterRequestDto.getImageList().size(); i++) {
        PopupImageUpsertRequestDto image = popupRegisterRequestDto.getImageList().get(i);
        imageList.add(
            PopupImage.builder()
                .popup(popup)
                .imageUrl(image.getImageUrl())
                .sortOrder(image.getSortOrder() != null ? image.getSortOrder() : i)
                .build());
      }
      popupImageRepository.saveAll(imageList);
    }

    // popup 추천 연결 저장
    if (popupRegisterRequestDto.getRecommendIdList() != null
        && !popupRegisterRequestDto.getRecommendIdList().isEmpty()) {
      List<Recommend> found =
          recommendRepository.findAllById(popupRegisterRequestDto.getRecommendIdList());
      if (found.size() != popupRegisterRequestDto.getRecommendIdList().size()) {
        throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
      }

      List<PopupRecommend> popupRecommendList = new ArrayList<>();
      for (Recommend recommend : found) {
        popupRecommendList.add(PopupRecommend.builder().popup(popup).recommend(recommend).build());
      }
      popupRecommendRepository.saveAll(popupRecommendList);
    }
    return popup;
  }
}
