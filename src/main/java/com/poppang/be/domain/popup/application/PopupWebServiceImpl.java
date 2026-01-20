package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.web.response.PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupWebServiceImpl implements PopupWebService {

  private final PopupRepository popupRepository;
  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final UsersRepository usersRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final UserFavoriteRepository userFavoriteRepository;

  private static final int RANDOM_LIMIT = 5;
  private static final int FAVORITE_LIMIT = 5;
  private static final int UPCOMING_LIMIT = 5;

  @Override
  @Transactional(readOnly = true)
  public List<PopupWebRandomResponseDto> getRandomPopupList() {

    List<PopupWebRandomResponseDto> randomPopupList =
        popupRepository.findRandomActiveWithThumbnail(RANDOM_LIMIT).stream()
            .map(
                r ->
                    PopupWebRandomResponseDto.builder()
                        .popupUuid(r.getPopupUuid())
                        .name(r.getPopupName())
                        .thumbnailUrl(r.getThumbnailUrl())
                        .build())
            .toList();

    return randomPopupList;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopupWebFavoriteResponseDto> getFavoritePopupList() {

    List<PopupWebFavoriteResponseDto> favorietPopupList =
        popupRepository.findTopViewedActiveWithThumbnail(FAVORITE_LIMIT).stream()
            .map(
                r ->
                    PopupWebFavoriteResponseDto.builder()
                        .popupUuid(r.getPopupUuid())
                        .name(r.getPopupName())
                        .thumbnailUrl(r.getThumbnailUrl())
                        .region(r.getRegion())
                        .startDate(r.getStartDate())
                        .endDate(r.getEndDate())
                        .build())
            .toList();

    return favorietPopupList;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopupWebUpcomingResponseDto> getUpcomingPopupList() {

    // 다가오는 팝업 시작일 기준 '+10일' 조회
    int upcomingDays = 10;

    LocalDate startDate = LocalDate.now().plusDays(1);
    LocalDate endDate = startDate.plusDays(upcomingDays);

    List<PopupWebUpcomingResponseDto> upcomingPopupList =
        popupRepository.findUpcomingActiveWithThumbnail(startDate, endDate, UPCOMING_LIMIT).stream()
            .map(
                r ->
                    PopupWebUpcomingResponseDto.builder()
                        .popupUuid(r.getPopupUuid())
                        .name(r.getPopupName())
                        .thumbnailUrl(r.getThumbnailUrl())
                        .region(r.getRegion())
                        .startDate(r.getStartDate())
                        .endDate(r.getEndDate())
                        .dDay((int) ChronoUnit.DAYS.between(LocalDate.now(), r.getStartDate()))
                        .build())
            .toList();

    return upcomingPopupList;
  }

  @Override
  @Transactional(readOnly = true)
  public PopupWebDetailResponseDto getPopupDetail(String popupUuid) {
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

    // DTO 조립
    PopupWebDetailResponseDto popupWebDetailResponseDto =
        PopupWebDetailResponseDto.builder()
            .popupUuid(popup.getUuid())
            .name(popup.getName())
            .startDate(popup.getStartDate())
            .endDate(popup.getEndDate())
            .openTime(popup.getOpenTime())
            .closeTime(popup.getCloseTime())
            .address(popup.getAddress())
            .roadAddress(popup.getRoadAddress())
            .region(popup.getRegion())
            .instaPostUrl(popup.getInstaPostUrl())
            .captionSummary(popup.getCaptionSummary())
            .imageUrlList(imageUrlList)
            .recommendList(recommendNameList)
            .favoriteCount(favoriteCount)
            .viewCount(viewCount)
            .build();

    return popupWebDetailResponseDto;
  }
}
