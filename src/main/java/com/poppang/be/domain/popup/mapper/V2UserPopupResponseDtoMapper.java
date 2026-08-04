package com.poppang.be.domain.popup.mapper;

import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.application.PopupCountBoostValue;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollItemResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class V2UserPopupResponseDtoMapper {

  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final PopupCountBoostService popupCountBoostService;

  public List<V2UserPopupResponseDto> toResponseDtoList(
      List<Popup> popupList, Set<Long> favoritePopupIdList) {
    if (popupList == null || popupList.isEmpty()) {
      return List.of();
    }
    Set<Long> favoritePopupIds =
        favoritePopupIdList == null ? Collections.emptySet() : favoritePopupIdList;

    List<Long> popupIdList = new ArrayList<>(popupList.size());
    List<String> popupUuidList = new ArrayList<>(popupList.size());
    for (Popup popup : popupList) {
      popupIdList.add(popup.getId());
      popupUuidList.add(popup.getUuid());
    }

    Map<Long, List<String>> imageMap = new HashMap<>();
    for (PopupImage image :
        popupImageRepository.findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(popupIdList)) {
      imageMap
          .computeIfAbsent(image.getPopup().getId(), key -> new ArrayList<>())
          .add(image.getImageUrl());
    }

    Map<Long, List<String>> recommendMap = new HashMap<>();
    for (PopupRecommend popupRecommend :
        popupRecommendRepository.findAllByPopupIdsWithRecommend(popupIdList)) {
      recommendMap
          .computeIfAbsent(popupRecommend.getPopup().getId(), key -> new ArrayList<>())
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
    List<V2UserPopupResponseDto> responseList = new ArrayList<>(popupList.size());
    for (Popup popup : popupList) {
      PopupCountBoostValue boostValue =
          boostValueMap.getOrDefault(popup.getId(), PopupCountBoostValue.ZERO);
      responseList.add(
          toResponseDto(
              popup,
              imageMap.getOrDefault(popup.getId(), List.of()),
              recommendMap.getOrDefault(popup.getId(), null),
              favoriteCountMap.getOrDefault(popup.getId(), 0L) + boostValue.favoriteCountBoost(),
              viewCountMap.getOrDefault(popup.getUuid(), 0L) + boostValue.viewCountBoost(),
              favoritePopupIds.contains(popup.getId())));
    }
    return responseList;
  }

  public V2UserPopupResponseDto toDetailResponseDto(Popup popup, String userUuid) {
    List<String> imageUrlList =
        popupImageRepository.findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(popup.getId()).stream()
            .map(PopupImage::getImageUrl)
            .toList();
    List<String> recommendNameList =
        popupRecommendRepository.findAllByPopup_Id(popup.getId()).stream()
            .map(popupRecommend -> popupRecommend.getRecommend().getRecommendName())
            .toList();
    long favoriteCount = userFavoriteRepository.countByPopupUuid(popup.getUuid());
    Long rawViewCount = popupTotalViewCountRepository.getViewCountByPopupUuid(popup.getUuid());
    long viewCount = rawViewCount == null ? 0L : rawViewCount;
    PopupCountBoostValue boostValue = popupCountBoostService.getBoostValue(popup.getId());
    boolean favorited =
        userFavoriteRepository.existsByUser_UuidAndPopup_Uuid(userUuid, popup.getUuid());

    return toResponseDto(
        popup,
        imageUrlList,
        recommendNameList,
        favoriteCount + boostValue.favoriteCountBoost(),
        viewCount + boostValue.viewCountBoost(),
        favorited);
  }

  public V2UserPopupScrollResponseDto toScrollResponseDto(
      List<Popup> popupList, String userUuid, boolean hasNext) {
    if (popupList == null || popupList.isEmpty()) {
      return new V2UserPopupScrollResponseDto(List.of(), null, false);
    }

    List<Long> popupIds = popupList.stream().map(Popup::getId).toList();
    Map<Long, String> thumbnailUrlByPopupId = new HashMap<>();
    for (PopupImage popupImage :
        popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(popupIds, 0)) {
      thumbnailUrlByPopupId.putIfAbsent(popupImage.getPopup().getId(), popupImage.getImageUrl());
    }
    Set<Long> favoritedPopupIds =
        new HashSet<>(userFavoriteRepository.findPopupIdsByUserUuidAndPopupIds(userUuid, popupIds));

    List<V2UserPopupScrollItemResponseDto> items =
        popupList.stream()
            .map(
                popup ->
                    new V2UserPopupScrollItemResponseDto(
                        popup.getUuid(),
                        thumbnailUrlByPopupId.get(popup.getId()),
                        popup.getRegion(),
                        popup.getName(),
                        popup.getStartDate(),
                        popup.getEndDate(),
                        favoritedPopupIds.contains(popup.getId())))
            .toList();
    Long nextCursor = hasNext ? popupList.get(popupList.size() - 1).getId() : null;
    return new V2UserPopupScrollResponseDto(items, nextCursor, hasNext);
  }

  private V2UserPopupResponseDto toResponseDto(
      Popup popup,
      List<String> imageUrlList,
      List<String> recommendList,
      long favoriteCount,
      long viewCount,
      boolean favorited) {
    return new V2UserPopupResponseDto(
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
        viewCount,
        favorited);
  }
}
