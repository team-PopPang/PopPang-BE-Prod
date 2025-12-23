package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.util.StringNormalizer;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.response.PopupUserResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupUserServiceImpl implements PopupUserService {

  private final PopupRepository popupRepository;
  private final PopupImageRepository popupImageRepository;
  private final RecommendRepository recommendRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final UsersRepository usersRepository;
  private final UserRecommendRepository userRecommendRepository;
  private final PopupUserResponseDtoMapper popupUserResponseDtoMapper;

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
            .favoriteCount(favoriteCount)
            .viewCount(viewCount)
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

    String normalizedRegion = StringNormalizer.normalizeRegion(region);
    String normalizedDistrict = StringNormalizer.normalizeDistrict(district);

    Set<Long> favoritedPopupIdList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid).stream()
            .map(f -> f.getPopup().getId())
            .collect(Collectors.toSet());

    if (homeSortStandard == HomeSortStandard.NEWEST) {
      List<Popup> popupList =
          popupRepository.findActiveByNewest(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else if (homeSortStandard == HomeSortStandard.CLOSING_SOON) {
      List<Popup> popupList =
          popupRepository.findActiveByClosingSoon(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else if (homeSortStandard == HomeSortStandard.MOST_FAVORITED) {
      List<Popup> popupList =
          popupRepository.findActiveByMostFavorited(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else if (homeSortStandard == HomeSortStandard.MOST_VIEWED) {
      List<Popup> popupList =
          popupRepository.findActiveByMostViewed(normalizedRegion, normalizedDistrict);

      return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
    } else {
      throw new BaseException(ErrorCode.INVALID_SORT_STANDARD);
    }
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

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
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

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }
}
