package com.poppang.be.domain.popup.mapper;

import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.application.PopupCountBoostValue;
import com.poppang.be.domain.popup.dto.v2.V2PopupResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class V2PopupResponseDtoMapper {

  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final PopupCountBoostService popupCountBoostService;

  public List<V2PopupResponseDto> toResponseDtoList(List<Popup> popupList) {
    if (popupList == null || popupList.isEmpty()) {
      return List.of();
    }

    List<Long> popupIdList = popupList.stream().map(Popup::getId).toList();
    List<String> popupUuidList = popupList.stream().map(Popup::getUuid).toList();

    Map<Long, List<String>> imageMap = new HashMap<>();
    for (PopupImage image :
        popupImageRepository.findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(popupIdList)) {
      imageMap
          .computeIfAbsent(image.getPopup().getId(), ignored -> new ArrayList<>())
          .add(image.getImageUrl());
    }

    Map<Long, List<String>> recommendMap = new HashMap<>();
    for (PopupRecommend popupRecommend :
        popupRecommendRepository.findAllByPopupIdsWithRecommend(popupIdList)) {
      recommendMap
          .computeIfAbsent(popupRecommend.getPopup().getId(), ignored -> new ArrayList<>())
          .add(popupRecommend.getRecommend().getRecommendName());
    }

    Map<Long, Long> favoriteCountMap = new HashMap<>();
    for (UserFavoriteRepository.FavoriteCountRow row :
        userFavoriteRepository.countAllByPopupIds(popupIdList)) {
      favoriteCountMap.put(row.getPopupId(), row.getCnt());
    }

    Map<String, Long> viewCountMap = new HashMap<>();
    for (PopupTotalViewCountRepository.ViewCountProjection row :
        popupTotalViewCountRepository.findAllViewCounts(popupUuidList)) {
      viewCountMap.put(row.getPopupUuid(), row.getViewCount() == null ? 0L : row.getViewCount());
    }

    Map<Long, PopupCountBoostValue> boostValueMap =
        popupCountBoostService.getBoostValueMap(popupIdList);

    List<V2PopupResponseDto> responses = new ArrayList<>(popupList.size());
    for (Popup popup : popupList) {
      PopupCountBoostValue boostValue =
          boostValueMap.getOrDefault(popup.getId(), PopupCountBoostValue.ZERO);
      responses.add(
          response(
              popup,
              imageMap.getOrDefault(popup.getId(), List.of()),
              recommendMap.getOrDefault(popup.getId(), null),
              favoriteCountMap.getOrDefault(popup.getId(), 0L) + boostValue.favoriteCountBoost(),
              viewCountMap.getOrDefault(popup.getUuid(), 0L) + boostValue.viewCountBoost()));
    }
    return responses;
  }

  public V2PopupResponseDto toDetailResponseDto(Popup popup) {
    List<String> imageUrlList =
        popupImageRepository.findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(popup.getId()).stream()
            .map(PopupImage::getImageUrl)
            .toList();
    List<String> recommendNameList =
        popupRecommendRepository.findAllByPopup_Id(popup.getId()).stream()
            .map(PopupRecommend::getRecommend)
            .map(recommend -> recommend.getRecommendName())
            .toList();
    long favoriteCount = userFavoriteRepository.countByPopupUuid(popup.getUuid());
    Long rawViewCount = popupTotalViewCountRepository.getViewCountByPopupUuid(popup.getUuid());
    long viewCount = rawViewCount == null ? 0L : rawViewCount;
    PopupCountBoostValue boostValue = popupCountBoostService.getBoostValue(popup.getId());

    return response(
        popup,
        imageUrlList,
        recommendNameList,
        favoriteCount + boostValue.favoriteCountBoost(),
        viewCount + boostValue.viewCountBoost());
  }

  private V2PopupResponseDto response(
      Popup popup,
      List<String> imageUrlList,
      List<String> recommendList,
      long favoriteCount,
      long viewCount) {
    return new V2PopupResponseDto(
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
        imageUrlList,
        popup.getMediaType(),
        recommendList,
        favoriteCount,
        viewCount);
  }
}
