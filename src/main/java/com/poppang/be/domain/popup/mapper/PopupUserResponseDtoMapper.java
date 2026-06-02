package com.poppang.be.domain.popup.mapper;

import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.application.PopupCountBoostValue;
import com.poppang.be.domain.popup.dto.app.response.PopupUserResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PopupUserResponseDtoMapper {

  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final PopupCountBoostService popupCountBoostService;

  public List<PopupUserResponseDto> toPopupUserResponseDtoList(
      List<Popup> popupList, Set<Long> favoritePopupIdList) {

    if (popupList == null || popupList.isEmpty()) {
      return List.of();
    }
    if (favoritePopupIdList == null) {
      favoritePopupIdList = Collections.emptySet();
    }

    // id/uuid 수집
    List<Long> popupIdList = new ArrayList<>(popupList.size());
    List<String> popupUuidList = new ArrayList<>(popupList.size());
    for (Popup popup : popupList) {
      popupIdList.add(popup.getId());
      popupUuidList.add(popup.getUuid());
    }

    // 팝업 이미지
    List<PopupImage> images =
        popupImageRepository.findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(popupIdList);
    Map<Long, List<String>> imageMap = new HashMap<>();
    for (PopupImage img : images) {
      imageMap
          .computeIfAbsent(img.getPopup().getId(), k -> new ArrayList<>())
          .add(img.getImageUrl());
    }

    // 추천
    List<PopupRecommend> recs =
        popupRecommendRepository.findAllByPopupIdsWithRecommend(popupIdList);
    Map<Long, List<String>> recommendMap = new HashMap<>();
    for (PopupRecommend r : recs) {
      recommendMap
          .computeIfAbsent(r.getPopup().getId(), k -> new ArrayList<>())
          .add(r.getRecommend().getRecommendName());
    }

    // 좋아요 수 배치
    Map<Long, Long> favoriteCountMap = new HashMap<>();
    for (var row : userFavoriteRepository.countAllByPopupIds(popupIdList)) {
      favoriteCountMap.put(row.getPopupId(), row.getCnt());
    }

    // 조회수 배치
    Map<String, Long> viewCountMap = new HashMap<>();
    for (var row : popupTotalViewCountRepository.findAllViewCounts(popupUuidList)) {
      viewCountMap.put(row.getPopupUuid(), row.getViewCount() == null ? 0L : row.getViewCount());
    }

    Map<Long, PopupCountBoostValue> boostValueMap =
        popupCountBoostService.getBoostValueMap(popupIdList);

    // DTO 조립 (+ isFavorited)
    List<PopupUserResponseDto> popupUserResponseDtoList = new ArrayList<>(popupList.size());
    for (Popup p : popupList) {
      boolean isFavorited = favoritePopupIdList.contains(p.getId());
      PopupCountBoostValue boostValue =
          boostValueMap.getOrDefault(p.getId(), PopupCountBoostValue.ZERO);

      popupUserResponseDtoList.add(
          PopupUserResponseDto.builder()
              .popupUuid(p.getUuid())
              .name(p.getName())
              .startDate(p.getStartDate())
              .endDate(p.getEndDate())
              .openTime(p.getOpenTime())
              .closeTime(p.getCloseTime())
              .address(p.getAddress())
              .roadAddress(p.getRoadAddress())
              .region(p.getRegion())
              .latitude(p.getLatitude())
              .longitude(p.getLongitude())
              .instaPostId(p.getInstaPostId())
              .instaPostUrl(p.getInstaPostUrl())
              .captionSummary(p.getCaptionSummary())
              .imageUrlList(imageMap.getOrDefault(p.getId(), List.of()))
              .mediaType(p.getMediaType())
              .recommendList(recommendMap.getOrDefault(p.getId(), null))
              .favoriteCount(
                  favoriteCountMap.getOrDefault(p.getId(), 0L) + boostValue.favoriteCountBoost())
              .viewCount(viewCountMap.getOrDefault(p.getUuid(), 0L) + boostValue.viewCountBoost())
              .favorited(isFavorited)
              .build());
    }

    return popupUserResponseDtoList;
  }
}
