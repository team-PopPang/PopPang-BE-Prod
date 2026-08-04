package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebSearchResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebUpcomingResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.mapper.V2PopupWebInProgressResponseDtoMapper;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class V2PopupWebServiceImpl implements V2PopupWebService {

  private static final int RANDOM_LIMIT = 5;
  private static final int FAVORITE_LIMIT = 5;
  private static final int UPCOMING_LIMIT = 5;
  private static final int UPCOMING_DAYS = 10;

  private final PopupRepository popupRepository;
  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupCountBoostService popupCountBoostService;
  private final PopupHomeFilterService popupHomeFilterService;
  private final V2PopupWebInProgressResponseDtoMapper inProgressResponseDtoMapper;

  @Override
  @Transactional(readOnly = true)
  public List<V2PopupWebRandomResponseDto> getRandomPopupList() {
    return popupRepository.findRandomActiveWithThumbnail(RANDOM_LIMIT).stream()
        .map(
            row ->
                new V2PopupWebRandomResponseDto(
                    row.getPopupUuid(), row.getPopupName(), row.getThumbnailUrl()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2PopupWebFavoriteResponseDto> getFavoritePopupList() {
    return popupRepository.findTopViewedActiveWithThumbnail(FAVORITE_LIMIT).stream()
        .map(
            row ->
                new V2PopupWebFavoriteResponseDto(
                    row.getPopupUuid(),
                    row.getPopupName(),
                    row.getThumbnailUrl(),
                    row.getRegion(),
                    row.getStartDate(),
                    row.getEndDate()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2PopupWebInProgressResponseDto> getInProgressPopupList(
      String region, String district, String sort) {
    if (region == null && district == null && sort == null) {
      return popupRepository.findInProgressActiveWithThumbnail().stream()
          .map(
              row ->
                  new V2PopupWebInProgressResponseDto(
                      row.getPopupUuid(),
                      row.getPopupName(),
                      row.getThumbnailUrl(),
                      row.getRegion(),
                      row.getStartDate(),
                      row.getEndDate()))
          .toList();
    }
    if (StringUtils.hasText(district) && !StringUtils.hasText(region)) {
      throw new BaseException(ErrorCode.REGION_REQUIRED_FOR_DISTRICT);
    }

    HomeSortStandard sortStandard = parseHomeSortStandard(sort);
    List<Popup> popups =
        popupHomeFilterService.getFilteredPopupList(region, district, sortStandard);
    return inProgressResponseDtoMapper.toResponseDtoList(popups);
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2PopupWebUpcomingResponseDto> getUpcomingPopupList() {
    LocalDate today = LocalDate.now();
    LocalDate startDate = today.plusDays(1);
    LocalDate endDate = startDate.plusDays(UPCOMING_DAYS);

    return popupRepository
        .findUpcomingActiveWithThumbnail(startDate, endDate, UPCOMING_LIMIT)
        .stream()
        .map(
            row ->
                new V2PopupWebUpcomingResponseDto(
                    row.getPopupUuid(),
                    row.getPopupName(),
                    row.getThumbnailUrl(),
                    row.getRegion(),
                    row.getStartDate(),
                    row.getEndDate(),
                    (int) ChronoUnit.DAYS.between(today, row.getStartDate())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2PopupWebSearchResponseDto> getSearchPopupList(String query) {
    String term = query == null ? "" : query.trim();
    if (term.isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SEARCH_QUERY);
    }

    return popupRepository.searchWebActiveWithThumbnail(term).stream()
        .map(
            row ->
                new V2PopupWebSearchResponseDto(
                    row.getPopupUuid(),
                    row.getPopupName(),
                    row.getThumbnailUrl(),
                    row.getRegion(),
                    row.getStartDate(),
                    row.getEndDate()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public V2PopupWebDetailResponseDto getPopupDetail(String popupUuid) {
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));
    List<String> imageUrls =
        popupImageRepository.findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(popup.getId()).stream()
            .map(PopupImage::getImageUrl)
            .toList();
    List<String> recommendNames =
        popupRecommendRepository.findAllByPopup_Id(popup.getId()).stream()
            .map(popupRecommend -> popupRecommend.getRecommend().getRecommendName())
            .toList();
    long favoriteCount = userFavoriteRepository.countByPopupUuid(popup.getUuid());
    Long rawViewCount = popupTotalViewCountRepository.getViewCountByPopupUuid(popup.getUuid());
    long viewCount = rawViewCount == null ? 0L : rawViewCount;
    PopupCountBoostValue boost = popupCountBoostService.getBoostValue(popup.getId());

    return new V2PopupWebDetailResponseDto(
        popup.getUuid(),
        popup.getName(),
        popup.getStartDate(),
        popup.getEndDate(),
        popup.getOpenTime(),
        popup.getCloseTime(),
        popup.getAddress(),
        popup.getRoadAddress(),
        popup.getRegion(),
        popup.getInstaPostUrl(),
        popup.getCaptionSummary(),
        imageUrls,
        recommendNames,
        favoriteCount + boost.favoriteCountBoost(),
        viewCount + boost.viewCountBoost());
  }

  private HomeSortStandard parseHomeSortStandard(String sort) {
    if (!StringUtils.hasText(sort)) {
      return HomeSortStandard.CLOSING_SOON;
    }
    try {
      return HomeSortStandard.valueOf(sort.trim());
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_SORT_STANDARD);
    }
  }
}
