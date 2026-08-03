package com.poppang.be.domain.popup.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.util.StringNormalizer;
import com.poppang.be.domain.popup.dto.v2.V2PopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2RegionDistrictsResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.enums.SortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.mapper.V2PopupResponseDtoMapper;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class V2PopupServiceImpl implements V2PopupService {

  private final PopupRepository popupRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UserRecommendRepository userRecommendRepository;
  private final UsersRepository usersRepository;
  private final PopupHomeFilterService popupHomeFilterService;
  private final V2PopupResponseDtoMapper popupResponseDtoMapper;
  private final ObjectMapper objectMapper;

  @Override
  public List<V2PopupResponseDto> getAllPopupList() {
    return popupResponseDtoMapper.toResponseDtoList(popupRepository.findAll());
  }

  @Override
  public V2PopupResponseDto getPopupByUuid(String popupUuid) {
    requirePopupUuid(popupUuid);
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));
    return popupResponseDtoMapper.toDetailResponseDto(popup);
  }

  @Override
  public List<V2PopupResponseDto> getSearchPopupList(String query) {
    String term = query == null ? "" : query.trim();
    if (term.isEmpty()) {
      return List.of();
    }
    return popupResponseDtoMapper.toResponseDtoList(popupRepository.searchActivatedByKeyword(term));
  }

  @Override
  public List<V2PopupResponseDto> getUpcomingPopupList(Integer upcomingDays) {
    int days = upcomingDays == null || upcomingDays <= 0 ? 10 : upcomingDays;
    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = startDate.plusDays(days);
    return popupResponseDtoMapper.toResponseDtoList(
        popupRepository.findByActivatedTrueAndStartDateBetween(startDate, endDate));
  }

  @Override
  public List<V2PopupResponseDto> getInProgressPopupList() {
    return popupResponseDtoMapper.toResponseDtoList(popupRepository.findInProgressPopupList());
  }

  @Override
  public List<V2RegionDistrictsResponseDto> getRegionDistricts() {
    List<V2RegionDistrictsResponseDto> responses = new ArrayList<>();
    for (PopupRepository.RegionDistrictsRaw row : popupRepository.findRegionDistrictsJson()) {
      try {
        List<String> districts =
            objectMapper.readValue(row.getDistricts(), new TypeReference<List<String>>() {});
        responses.add(new V2RegionDistrictsResponseDto(row.getRegion(), districts));
      } catch (JsonProcessingException exception) {
        throw new BaseException(ErrorCode.REGION_DISTRICTS_JSON_PARSE_ERROR);
      }
    }
    return responses;
  }

  @Override
  public List<V2PopupResponseDto> getRandomPopupList() {
    return popupResponseDtoMapper.toResponseDtoList(popupRepository.findRandomActivePopups());
  }

  @Override
  public List<V2PopupResponseDto> getFilteredPopupList(
      String region,
      String district,
      SortStandard sortStandard,
      Double latitude,
      Double longitude) {
    String normalizedDistrict = StringNormalizer.normalizeDistrict(district);
    List<Popup> popupList;

    if (sortStandard == SortStandard.LIKES) {
      popupList = popupRepository.findPopupListByRegionAndLikes(region, normalizedDistrict);
    } else {
      popupList =
          popupRepository.findPopupListByRegionAndDistance(
              region, normalizedDistrict, latitude, longitude);
    }

    return popupResponseDtoMapper.toResponseDtoList(popupList);
  }

  @Override
  public List<V2PopupResponseDto> getFilteredHomePopupList(
      String region, String district, HomeSortStandard homeSortStandard) {
    return popupResponseDtoMapper.toResponseDtoList(
        popupHomeFilterService.getFilteredPopupList(region, district, homeSortStandard));
  }

  @Override
  public List<V2PopupResponseDto> getFilteredMapPopupList(
      String region,
      String district,
      Double latitude,
      Double longitude,
      MapSortStandard mapSortStandard) {
    String normalizedRegion = StringNormalizer.normalizeRegion(region);
    String normalizedDistrict = StringNormalizer.normalizeDistrict(district);
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

    return popupResponseDtoMapper.toResponseDtoList(popupList);
  }

  @Override
  public List<V2PopupResponseDto> getRelatedPopupList(String popupUuid) {
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
    relatedPopupList.removeIf(relatedPopup -> relatedPopup.getId().equals(popup.getId()));
    List<Popup> popupList = relatedPopupList.stream().distinct().limit(10).toList();

    if (popupList.size() == 10) {
      return popupResponseDtoMapper.toResponseDtoList(popupList);
    }

    int remain = 10 - popupList.size();
    List<Long> excludeIds = new ArrayList<>(popupList.size() + 1);
    excludeIds.add(popup.getId());
    excludeIds.addAll(popupList.stream().map(Popup::getId).toList());
    List<Popup> randomPopups =
        popupRepository.findRandomActivePopupsExcluding(excludeIds, excludeIds.size(), remain);
    List<Popup> finalPopupList = new ArrayList<>(10);
    finalPopupList.addAll(popupList);
    finalPopupList.addAll(randomPopups);
    return popupResponseDtoMapper.toResponseDtoList(finalPopupList);
  }

  @Override
  public List<V2PopupResponseDto> getRecommendationPopupList(Long recommendId) {
    return popupResponseDtoMapper.toResponseDtoList(
        popupRepository.findActivePopupsByRecommendId(recommendId));
  }

  @Override
  public List<V2PopupResponseDto> getRecommendPopupList(String userUuid) {
    usersRepository
        .findByUuid(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    List<UserRecommend> userRecommendList = userRecommendRepository.findAllByUser_Uuid(userUuid);
    Set<Long> pickedPopupIds = new HashSet<>();
    List<Popup> popupList = new ArrayList<>(10);

    for (UserRecommend userRecommend : userRecommendList) {
      Long recommendId = userRecommend.getRecommend().getId();
      List<Popup> matchedPopupList =
          popupRecommendRepository.findActivePopupsByRecommendId(recommendId, PageRequest.of(0, 2));

      for (Popup popup : matchedPopupList) {
        if (pickedPopupIds.add(popup.getId())) {
          popupList.add(popup);
          if (popupList.size() == 10) {
            break;
          }
        }
      }
      if (popupList.size() == 10) {
        break;
      }
    }

    if (popupList.size() < 10) {
      int remain = 10 - popupList.size();
      List<Long> excludeIds = new ArrayList<>(pickedPopupIds);
      List<Popup> randomPopups =
          popupRepository.findRandomActivePopupsExcluding(excludeIds, excludeIds.size(), remain);
      for (Popup popup : randomPopups) {
        if (pickedPopupIds.add(popup.getId())) {
          popupList.add(popup);
        }
      }
    }

    return popupResponseDtoMapper.toResponseDtoList(popupList);
  }

  private void requirePopupUuid(String popupUuid) {
    if (popupUuid == null || popupUuid.isBlank()) {
      throw new BaseException(ErrorCode.POPUP_NOT_FOUND);
    }
  }
}
