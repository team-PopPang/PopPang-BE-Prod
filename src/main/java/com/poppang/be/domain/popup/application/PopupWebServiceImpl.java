package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.web.response.PopupWebFavoriteResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebRandomResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebUpcomingResponseDto;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
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
}
