package com.poppang.be.domain.favorite.application;

import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.application.PopupCountBoostValue;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class V2FavoritePopupResponseDtoMapper {

  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final PopupCountBoostService popupCountBoostService;

  public List<V2FavoritePopupResponseDto> toResponseList(
      List<Popup> popups, Set<Long> favoritePopupIds) {
    if (popups == null || popups.isEmpty()) {
      return List.of();
    }
    Set<Long> normalizedFavoritePopupIds =
        favoritePopupIds == null ? Collections.emptySet() : favoritePopupIds;

    List<Long> popupIds = new ArrayList<>(popups.size());
    List<String> popupUuids = new ArrayList<>(popups.size());
    for (Popup popup : popups) {
      popupIds.add(popup.getId());
      popupUuids.add(popup.getUuid());
    }

    List<PopupImage> images =
        popupImageRepository.findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(popupIds);
    Map<Long, List<String>> imageMap = new HashMap<>();
    for (PopupImage image : images) {
      imageMap
          .computeIfAbsent(image.getPopup().getId(), ignored -> new ArrayList<>())
          .add(image.getImageUrl());
    }

    List<PopupRecommend> recommends =
        popupRecommendRepository.findAllByPopupIdsWithRecommend(popupIds);
    Map<Long, List<String>> recommendMap = new HashMap<>();
    for (PopupRecommend recommend : recommends) {
      recommendMap
          .computeIfAbsent(recommend.getPopup().getId(), ignored -> new ArrayList<>())
          .add(recommend.getRecommend().getRecommendName());
    }

    Map<Long, Long> favoriteCountMap = new HashMap<>();
    for (var row : userFavoriteRepository.countAllByPopupIds(popupIds)) {
      favoriteCountMap.put(row.getPopupId(), row.getCnt());
    }

    Map<String, Long> viewCountMap = new HashMap<>();
    for (var row : popupTotalViewCountRepository.findAllViewCounts(popupUuids)) {
      viewCountMap.put(row.getPopupUuid(), row.getViewCount() == null ? 0L : row.getViewCount());
    }

    Map<Long, PopupCountBoostValue> boostValueMap =
        popupCountBoostService.getBoostValueMap(popupIds);

    List<V2FavoritePopupResponseDto> responses = new ArrayList<>(popups.size());
    for (Popup popup : popups) {
      PopupCountBoostValue boost =
          boostValueMap.getOrDefault(popup.getId(), PopupCountBoostValue.ZERO);
      responses.add(
          new V2FavoritePopupResponseDto(
              popup.getUuid(),
              popup.getName(),
              popup.getStartDate(),
              popup.getEndDate(),
              popup.getOpenTime(),
              popup.getCloseTime(),
              popup.getAddress(),
              popup.getRoadAddress(),
              popup.getRegion(),
              popup.getLatitude(),
              popup.getLongitude(),
              popup.getInstaPostId(),
              popup.getInstaPostUrl(),
              popup.getCaptionSummary(),
              imageMap.getOrDefault(popup.getId(), List.of()),
              popup.getMediaType(),
              recommendMap.getOrDefault(popup.getId(), null),
              favoriteCountMap.getOrDefault(popup.getId(), 0L) + boost.favoriteCountBoost(),
              viewCountMap.getOrDefault(popup.getUuid(), 0L) + boost.viewCountBoost(),
              normalizedFavoritePopupIds.contains(popup.getId())));
    }
    return responses;
  }
}
