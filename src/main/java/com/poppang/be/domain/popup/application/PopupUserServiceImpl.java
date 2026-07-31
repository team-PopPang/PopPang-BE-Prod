package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.util.StringNormalizer;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.app.response.PopupScrollItemResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupScrollResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupUserResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupAdvertisement;
import com.poppang.be.domain.popup.entity.PopupAdvertisementPlacement;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupAdvertisementRepository;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.mapper.PopupUserResponseDtoMapper;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
public class PopupUserServiceImpl implements PopupUserService {

  private static final int RECOMMEND_POPUP_LIMIT = 10;
  private static final int SCROLL_PAGE_SIZE = 15;
  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final PopupRepository popupRepository;
  private final PopupAdvertisementRepository popupAdvertisementRepository;
  private final PopupImageRepository popupImageRepository;
  private final RecommendRepository recommendRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final UsersRepository usersRepository;
  private final UserRecommendRepository userRecommendRepository;
  private final PopupUserResponseDtoMapper popupUserResponseDtoMapper;
  private final PopupCountBoostService popupCountBoostService;
  private final PopupHomeFilterService popupHomeFilterService;

  @Override
  @Transactional(readOnly = true)
  public List<PopupUserResponseDto> getAllPopupList(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<Popup> popupList = popupRepository.findAll();
    if (popupList.isEmpty()) {
      return List.of();
    }
    // 유저가 찜한 팝업 id 리스트
    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }

  @Override
  @Transactional(readOnly = true)
  public PopupScrollResponseDto getScrollPopupList(String userUuid, Long cursor) {
    usersRepository
        .findByUuid(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    LocalDate today = LocalDate.now(KOREA_ZONE_ID);
    PageRequest pageRequest = PageRequest.of(0, SCROLL_PAGE_SIZE);
    Slice<Popup> popupSlice =
        cursor == null
            ? popupRepository.findByActivatedTrueAndEndDateGreaterThanEqualOrderByIdDesc(
                today, pageRequest)
            : popupRepository
                .findByActivatedTrueAndEndDateGreaterThanEqualAndIdLessThanOrderByIdDesc(
                    today, cursor, pageRequest);
    List<Popup> popupList = popupSlice.getContent();
    if (popupList.isEmpty()) {
      return new PopupScrollResponseDto(List.of(), null, false);
    }

    List<Long> popupIdList = popupList.stream().map(Popup::getId).toList();
    Map<Long, String> thumbnailUrlByPopupId =
        popupImageRepository
            .findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(popupIdList, 0)
            .stream()
            .collect(
                Collectors.toMap(
                    popupImage -> popupImage.getPopup().getId(),
                    PopupImage::getImageUrl,
                    (first, ignored) -> first));
    Set<Long> favoritedPopupIdSet =
        new HashSet<>(
            userFavoriteRepository.findPopupIdsByUserUuidAndPopupIds(userUuid, popupIdList));

    List<PopupScrollItemResponseDto> itemList =
        popupList.stream()
            .map(
                popup ->
                    new PopupScrollItemResponseDto(
                        popup.getUuid(),
                        thumbnailUrlByPopupId.get(popup.getId()),
                        popup.getRegion(),
                        popup.getName(),
                        popup.getStartDate(),
                        popup.getEndDate(),
                        favoritedPopupIdSet.contains(popup.getId())))
            .toList();
    Long nextCursor = popupSlice.hasNext() ? popupList.get(popupList.size() - 1).getId() : null;

    return new PopupScrollResponseDto(itemList, nextCursor, popupSlice.hasNext());
  }

  private List<Popup> prependAdvertisementPopups(List<Popup> recommendedPopupList) {
    List<Popup> advertisementPopupList = findActiveAdvertisementPopupList();
    if (advertisementPopupList.isEmpty()) {
      return recommendedPopupList;
    }

    Set<Long> advertisementPopupIdSet =
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
      if (!advertisementPopupIdSet.contains(recommendedPopup.getId())) {
        finalPopupList.add(recommendedPopup);
      }
    }

    return finalPopupList;
  }

  private List<Popup> findActiveAdvertisementPopupList() {
    List<PopupAdvertisement> advertisements =
        popupAdvertisementRepository.findActiveAdvertisements(
            PopupAdvertisementPlacement.USER_RECOMMEND_TOP, LocalDateTime.now());
    if (advertisements.isEmpty()) {
      return List.of();
    }

    List<Long> popupIdList =
        advertisements.stream().map(PopupAdvertisement::getPopupId).distinct().toList();
    if (popupIdList.isEmpty()) {
      return List.of();
    }

    Map<Long, Popup> popupMap =
        popupRepository.findActiveInProgressByIdIn(popupIdList).stream()
            .collect(Collectors.toMap(Popup::getId, popup -> popup));

    Set<Long> pickedPopupIdSet = new HashSet<>();
    List<Popup> advertisementPopupList = new ArrayList<>(popupIdList.size());
    for (PopupAdvertisement advertisement : advertisements) {
      Long popupId = advertisement.getPopupId();
      Popup popup = popupMap.get(popupId);
      if (popup != null && pickedPopupIdSet.add(popupId)) {
        advertisementPopupList.add(popup);
      }
    }

    return advertisementPopupList;
  }

  @Override
  @Transactional(readOnly = true)
  public PopupUserResponseDto getPopupByUuid(String userUuid, String popupUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    // 팝업 이미지
    List<String> imageUrlList =
        popupImageRepository.findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(popup.getId()).stream()
            .map(PopupImage::getImageUrl)
            .toList();

    // 추천
    List<String> recommendNameList =
        popupRecommendRepository.findAllByPopup_Id(popup.getId()).stream()
            .map(r -> r.getRecommend().getRecommendName())
            .toList();

    // 좋아요 수
    Long favoriteCount = userFavoriteRepository.countByPopupUuid(popup.getUuid());

    // 조회 수
    Long rawViewCount = popupTotalViewCountRepository.getViewCountByPopupUuid(popup.getUuid());
    long viewCount = (rawViewCount == null) ? 0L : rawViewCount;
    PopupCountBoostValue boostValue = popupCountBoostService.getBoostValue(popup.getId());

    // 좋아요 여부
    boolean isFavorited =
        userFavoriteRepository.existsByUser_UuidAndPopup_Uuid(userUuid, popupUuid);

    // DTO 조립
    PopupUserResponseDto popupUserResponseDto =
        PopupUserResponseDto.builder()
            .popupUuid(popup.getUuid())
            .name(popup.getName())
            .startDate(popup.getStartDate())
            .endDate(popup.getEndDate())
            .openTime(popup.getOpenTime())
            .closeTime(popup.getCloseTime())
            .address(popup.getAddress())
            .roadAddress(popup.getRoadAddress())
            .region(popup.getRegion())
            .latitude(popup.getLatitude())
            .longitude(popup.getLongitude())
            .instaPostId(popup.getInstaPostId())
            .instaPostUrl(popup.getInstaPostUrl())
            .captionSummary(popup.getCaptionSummary())
            .imageUrlList(imageUrlList)
            .mediaType(popup.getMediaType())
            .recommendList(recommendNameList)
            .favoriteCount(favoriteCount + boostValue.favoriteCountBoost())
            .viewCount(viewCount + boostValue.viewCountBoost())
            .favorited(isFavorited)
            .build();

    return popupUserResponseDto;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopupUserResponseDto> getUpcomingPopupList(String userUuid, Integer upcomingDays) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    int days = (upcomingDays == null || upcomingDays <= 0) ? 10 : upcomingDays;

    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = startDate.plusDays(days);

    List<Popup> popupList =
        popupRepository.findByActivatedTrueAndStartDateBetween(startDate, endDate);

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }

  @Override
  public List<PopupUserResponseDto> getSearchPopupList(String userUuid, String q) {

    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    String term = (q == null ? "" : q.trim());
    if (term.isEmpty()) return List.of();

    List<Popup> popupList = popupRepository.searchActivatedByKeyword(term);

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }

  @Override
  public List<PopupUserResponseDto> getInProgressPopupList(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<Popup> popupList = popupRepository.findInProgressPopupList();

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }

  @Override
  public List<PopupUserResponseDto> getFilteredHomePopupList(
      String userUuid, String region, String district, HomeSortStandard homeSortStandard) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    List<Popup> popupList =
        popupHomeFilterService.getFilteredPopupList(region, district, homeSortStandard);
    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }

  @Override
  public List<PopupUserResponseDto> getFilteredMapPopupList(
      String userUuid,
      String region,
      String district,
      Double latitude,
      Double longitude,
      MapSortStandard mapSortStandard) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    String normalizedRegion = StringNormalizer.normalizeRegion(region);
    String normalizedDistrict = StringNormalizer.normalizeDistrict(district);

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    if (mapSortStandard == MapSortStandard.CLOSEST) {
      List<Popup> popupList =
          popupRepository.findActiveByClosest(
              normalizedRegion, normalizedDistrict, latitude, longitude);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else if (mapSortStandard == MapSortStandard.NEWEST) {
      List<Popup> popupList =
          popupRepository.findActiveByNewest(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else if (mapSortStandard == MapSortStandard.CLOSING_SOON) {
      List<Popup> popupList =
          popupRepository.findActiveByClosingSoon(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else if (mapSortStandard == MapSortStandard.MOST_FAVORITED) {
      List<Popup> popupList =
          popupRepository.findActiveByMostFavorited(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else if (mapSortStandard == MapSortStandard.MOST_VIEWED) {
      List<Popup> popupList =
          popupRepository.findActiveByMostViewed(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else {
      throw new BaseException(ErrorCode.INVALID_SORT_STANDARD);
    }
  }

  @Override
  public List<PopupUserResponseDto> getRecommendPopupList(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<UserRecommend> userRecommendList = userRecommendRepository.findAllByUser_Uuid(userUuid);

    Set<Long> pickedPopupIdSetList = new HashSet<>();
    List<Popup> popupList = new ArrayList<>(10);

    for (UserRecommend userRecommend : userRecommendList) {
      Long recommendId = userRecommend.getRecommend().getId();

      List<Popup> matchedPopupList =
          popupRecommendRepository.findActivePopupsByRecommendId(recommendId, PageRequest.of(0, 2));

      for (Popup popup : matchedPopupList) {
        if (pickedPopupIdSetList.add(popup.getId())) { // 중복 제거
          popupList.add(popup);
          if (popupList.size() == 10) {
            break; // 10개 채우면 바로 종료
          }
        }
      }

      if (popupList.size() == 10) {
        break;
      }
    }

    if (popupList.size() < 10) {
      int remain = 10 - popupList.size();

      List<Long> excludeIds = new ArrayList<>(pickedPopupIdSetList); // 이미 뽑은 것 제외
      List<Popup> randomPopups =
          popupRepository.findRandomActivePopupsExcluding(excludeIds, excludeIds.size(), remain);

      for (Popup popup : randomPopups) {
        if (pickedPopupIdSetList.add(popup.getId())) {
          popupList.add(popup);
        }
      }
    }

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    List<Popup> finalPopupList = prependAdvertisementPopups(popupList);

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(
        finalPopupList, favoritedPopupIdList);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopupUserResponseDto> getRelatedPopupList(String userUuid, String popupUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    PopupRecommend popupRecommend =
        popupRecommendRepository
            .findByPopupId(popup.getId())
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_RECOMMEND_NOT_FOUND));

    Long recommendId = popupRecommend.getRecommend().getId();

    List<Popup> relatedPopupList = popupRecommendRepository.findRelatedActivePopupList(recommendId);
    relatedPopupList.removeIf(p -> p.getId().equals(popup.getId()));

    List<Popup> popupList = relatedPopupList.stream().distinct().limit(10).toList();

    if (popupList.size() == 10) {
      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    }

    int remain = 10 - popupList.size();
    List<Long> excludeIds =
        popupList.stream() // 이미 뽑은 것 제외
            .map(Popup::getId)
            .toList();

    List<Popup> randomPopups =
        popupRepository.findRandomActivePopupsExcluding(excludeIds, excludeIds.size(), remain);

    List<Popup> finalPopupList = new ArrayList<>(10);
    finalPopupList.addAll(popupList);
    finalPopupList.addAll(randomPopups);

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(
        finalPopupList, favoritedPopupIdList);
  }

  @Override
  public List<PopupUserResponseDto> getRandomPopupList(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    List<Popup> popupList = popupRepository.findRandomActivePopups();
    List<Popup> finalPopupList = prependAdvertisementPopups(popupList);

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(
        finalPopupList, favoritedPopupIdList);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopupUserResponseDto> getRecommendationPopupList(String userUuid, Long recommendId) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    List<Popup> popupList = popupRepository.findActivePopupsByRecommendId(recommendId);

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }
}
