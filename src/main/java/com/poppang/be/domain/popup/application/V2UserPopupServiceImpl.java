package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.util.StringNormalizer;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupAdvertisement;
import com.poppang.be.domain.popup.entity.PopupAdvertisementPlacement;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupAdvertisementRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.mapper.V2UserPopupResponseDtoMapper;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2UserPopupServiceImpl implements V2UserPopupService {

  private static final int RECOMMEND_POPUP_LIMIT = 10;
  private static final int SCROLL_PAGE_SIZE = 15;
  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final PopupRepository popupRepository;
  private final PopupAdvertisementRepository popupAdvertisementRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final UsersRepository usersRepository;
  private final V2UserPopupResponseDtoMapper popupResponseDtoMapper;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserRecommendRepository userRecommendRepository;
  private final PopupHomeFilterService popupHomeFilterService;

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getAllPopupList(String userUuid) {
    requireUser(userUuid);
    List<Popup> popupList = popupRepository.findAll();
    if (popupList.isEmpty()) {
      return List.of();
    }
    return popupResponseDtoMapper.toResponseDtoList(popupList, getFavoritePopupIds(userUuid));
  }

  @Override
  @Transactional(readOnly = true)
  public V2UserPopupResponseDto getPopupByUuid(String userUuid, String popupUuid) {
    requireUser(userUuid);
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));
    return popupResponseDtoMapper.toDetailResponseDto(popup, userUuid);
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getUpcomingPopupList(String userUuid, Integer upcomingDays) {
    requireUser(userUuid);
    int days = upcomingDays == null || upcomingDays <= 0 ? 10 : upcomingDays;
    LocalDate startDate = LocalDate.now(KOREA_ZONE_ID).plusDays(1);
    LocalDate endDate = startDate.plusDays(days);
    List<Popup> popupList =
        popupRepository.findByActivatedTrueAndStartDateBetween(startDate, endDate);
    return popupResponseDtoMapper.toResponseDtoList(popupList, getFavoritePopupIds(userUuid));
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getSearchPopupList(String userUuid, String query) {
    requireUser(userUuid);
    String term = query == null ? "" : query.trim();
    if (term.isEmpty()) {
      return List.of();
    }
    List<Popup> popupList = popupRepository.searchActivatedByKeyword(term);
    return popupResponseDtoMapper.toResponseDtoList(popupList, getFavoritePopupIds(userUuid));
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getInProgressPopupList(String userUuid) {
    requireUser(userUuid);
    List<Popup> popupList = popupRepository.findInProgressPopupList();
    return popupResponseDtoMapper.toResponseDtoList(popupList, getFavoritePopupIds(userUuid));
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getRandomPopupList(String userUuid) {
    requireUser(userUuid);
    Set<Long> favoritePopupIds = getFavoritePopupIds(userUuid);
    List<Popup> popupList = popupRepository.findRandomActivePopups();
    return popupResponseDtoMapper.toResponseDtoList(
        prependAdvertisementPopups(popupList), favoritePopupIds);
  }

  @Override
  @Transactional(readOnly = true)
  public V2UserPopupScrollResponseDto getScrollPopupList(String userUuid, Long cursor) {
    requireUser(userUuid);
    LocalDate today = LocalDate.now(KOREA_ZONE_ID);
    PageRequest pageRequest = PageRequest.of(0, SCROLL_PAGE_SIZE);
    Slice<Popup> popupSlice =
        cursor == null
            ? popupRepository.findByActivatedTrueAndEndDateGreaterThanEqualOrderByIdDesc(
                today, pageRequest)
            : popupRepository
                .findByActivatedTrueAndEndDateGreaterThanEqualAndIdLessThanOrderByIdDesc(
                    today, cursor, pageRequest);
    return popupResponseDtoMapper.toScrollResponseDto(
        popupSlice.getContent(), userUuid, popupSlice.hasNext());
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getFilteredHomePopupList(
      String userUuid, String region, String district, HomeSortStandard homeSortStandard) {
    requireUser(userUuid);
    Set<Long> favoritePopupIds = getFavoritePopupIds(userUuid);
    List<Popup> popupList =
        popupHomeFilterService.getFilteredPopupList(region, district, homeSortStandard);
    return popupResponseDtoMapper.toResponseDtoList(popupList, favoritePopupIds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getFilteredMapPopupList(
      String userUuid,
      String region,
      String district,
      Double latitude,
      Double longitude,
      MapSortStandard mapSortStandard) {
    requireUser(userUuid);
    String normalizedRegion = StringNormalizer.normalizeRegion(region);
    String normalizedDistrict = StringNormalizer.normalizeDistrict(district);
    Set<Long> favoritePopupIds = getFavoritePopupIds(userUuid);

    List<Popup> popupList;
    if (mapSortStandard == MapSortStandard.CLOSEST) {
      popupList =
          popupRepository.findActiveByClosest(
              normalizedRegion, normalizedDistrict, latitude, longitude);
    } else if (mapSortStandard == MapSortStandard.NEWEST) {
      popupList = popupRepository.findActiveByNewest(normalizedRegion, normalizedDistrict);
    } else if (mapSortStandard == MapSortStandard.CLOSING_SOON) {
      popupList = popupRepository.findActiveByClosingSoon(normalizedRegion, normalizedDistrict);
    } else if (mapSortStandard == MapSortStandard.MOST_FAVORITED) {
      popupList = popupRepository.findActiveByMostFavorited(normalizedRegion, normalizedDistrict);
    } else if (mapSortStandard == MapSortStandard.MOST_VIEWED) {
      popupList = popupRepository.findActiveByMostViewed(normalizedRegion, normalizedDistrict);
    } else {
      throw new BaseException(ErrorCode.INVALID_SORT_STANDARD);
    }
    return popupResponseDtoMapper.toResponseDtoList(popupList, favoritePopupIds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getRecommendPopupList(String userUuid) {
    requireUser(userUuid);
    List<UserRecommend> userRecommendList = userRecommendRepository.findAllByUser_Uuid(userUuid);
    Set<Long> pickedPopupIds = new HashSet<>();
    List<Popup> popupList = new ArrayList<>(RECOMMEND_POPUP_LIMIT);

    for (UserRecommend userRecommend : userRecommendList) {
      Long recommendId = userRecommend.getRecommend().getId();
      List<Popup> matchedPopups =
          popupRecommendRepository.findActivePopupsByRecommendId(recommendId, PageRequest.of(0, 2));
      for (Popup popup : matchedPopups) {
        if (pickedPopupIds.add(popup.getId())) {
          popupList.add(popup);
          if (popupList.size() == RECOMMEND_POPUP_LIMIT) {
            break;
          }
        }
      }
      if (popupList.size() == RECOMMEND_POPUP_LIMIT) {
        break;
      }
    }

    if (popupList.size() < RECOMMEND_POPUP_LIMIT) {
      int remaining = RECOMMEND_POPUP_LIMIT - popupList.size();
      List<Long> excludedPopupIds = new ArrayList<>(pickedPopupIds);
      List<Popup> randomPopups =
          popupRepository.findRandomActivePopupsExcluding(
              excludedPopupIds, excludedPopupIds.size(), remaining);
      for (Popup popup : randomPopups) {
        if (pickedPopupIds.add(popup.getId())) {
          popupList.add(popup);
        }
      }
    }

    Set<Long> favoritePopupIds = getFavoritePopupIds(userUuid);
    return popupResponseDtoMapper.toResponseDtoList(
        prependAdvertisementPopups(popupList), favoritePopupIds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getRelatedPopupList(String userUuid, String popupUuid) {
    requireUser(userUuid);
    Set<Long> favoritePopupIds = getFavoritePopupIds(userUuid);
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));
    PopupRecommend popupRecommend =
        popupRecommendRepository
            .findByPopupId(popup.getId())
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_RECOMMEND_NOT_FOUND));

    List<Popup> relatedPopups =
        popupRecommendRepository.findRelatedActivePopupList(popupRecommend.getRecommend().getId());
    List<Popup> popupList =
        relatedPopups.stream()
            .filter(candidate -> !candidate.getId().equals(popup.getId()))
            .distinct()
            .limit(RECOMMEND_POPUP_LIMIT)
            .toList();
    if (popupList.size() == RECOMMEND_POPUP_LIMIT) {
      return popupResponseDtoMapper.toResponseDtoList(popupList, favoritePopupIds);
    }

    int remaining = RECOMMEND_POPUP_LIMIT - popupList.size();
    List<Long> excludedPopupIds = popupList.stream().map(Popup::getId).toList();
    List<Popup> randomPopups =
        popupRepository.findRandomActivePopupsExcluding(
            excludedPopupIds, excludedPopupIds.size(), remaining);
    List<Popup> finalPopupList = new ArrayList<>(RECOMMEND_POPUP_LIMIT);
    finalPopupList.addAll(popupList);
    finalPopupList.addAll(randomPopups);
    return popupResponseDtoMapper.toResponseDtoList(finalPopupList, favoritePopupIds);
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2UserPopupResponseDto> getRecommendationPopupList(
      String userUuid, Long recommendId) {
    requireUser(userUuid);
    Set<Long> favoritePopupIds = getFavoritePopupIds(userUuid);
    List<Popup> popupList = popupRepository.findActivePopupsByRecommendId(recommendId);
    return popupResponseDtoMapper.toResponseDtoList(popupList, favoritePopupIds);
  }

  private void requireUser(String userUuid) {
    usersRepository
        .findByUuid(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
  }

  private Set<Long> getFavoritePopupIds(String userUuid) {
    return userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
        .map(favorite -> favorite.getPopup().getId())
        .collect(Collectors.toSet());
  }

  private List<Popup> prependAdvertisementPopups(List<Popup> recommendedPopupList) {
    List<Popup> advertisementPopupList = findActiveAdvertisementPopupList();
    if (advertisementPopupList.isEmpty()) {
      return recommendedPopupList;
    }

    Set<Long> advertisementPopupIds =
        advertisementPopupList.stream().map(Popup::getId).collect(Collectors.toSet());
    List<Popup> finalPopupList = new ArrayList<>(RECOMMEND_POPUP_LIMIT);
    for (Popup advertisementPopup : advertisementPopupList) {
      if (finalPopupList.size() == RECOMMEND_POPUP_LIMIT) {
        return finalPopupList;
      }
      finalPopupList.add(advertisementPopup);
    }
    for (Popup recommendedPopup : recommendedPopupList) {
      if (finalPopupList.size() == RECOMMEND_POPUP_LIMIT) {
        break;
      }
      if (!advertisementPopupIds.contains(recommendedPopup.getId())) {
        finalPopupList.add(recommendedPopup);
      }
    }
    return finalPopupList;
  }

  private List<Popup> findActiveAdvertisementPopupList() {
    List<PopupAdvertisement> advertisements =
        popupAdvertisementRepository.findActiveAdvertisements(
            PopupAdvertisementPlacement.USER_RECOMMEND_TOP, LocalDateTime.now(KOREA_ZONE_ID));
    if (advertisements.isEmpty()) {
      return List.of();
    }

    List<Long> popupIds =
        advertisements.stream().map(PopupAdvertisement::getPopupId).distinct().toList();
    if (popupIds.isEmpty()) {
      return List.of();
    }

    Map<Long, Popup> popupMap = new HashMap<>();
    for (Popup popup : popupRepository.findActiveInProgressByIdIn(popupIds)) {
      popupMap.put(popup.getId(), popup);
    }
    Set<Long> pickedPopupIds = new HashSet<>();
    List<Popup> advertisementPopupList = new ArrayList<>(popupIds.size());
    for (PopupAdvertisement advertisement : advertisements) {
      Long popupId = advertisement.getPopupId();
      Popup popup = popupMap.get(popupId);
      if (popup != null && pickedPopupIds.add(popupId)) {
        advertisementPopupList.add(popup);
      }
    }
    return advertisementPopupList;
  }
}
