package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.response.PopupUserResponseDto;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import java.util.List;

public interface PopupUserService {

  List<PopupUserResponseDto> getAllPopupList(String userUuid);

  PopupUserResponseDto getPopupByUuid(String userUuid, String popupUuid);

  List<PopupUserResponseDto> getUpcomingPopupList(String userUuid, Integer upcomingDays);

  List<PopupUserResponseDto> getSearchPopupList(String userUuid, String q);

  List<PopupUserResponseDto> getInProgressPopupList(String userUuid);

  List<PopupUserResponseDto> getFilteredHomePopupList(
      String userUuid, String region, String district, HomeSortStandard homeSortStandard);

  List<PopupUserResponseDto> getFilteredMapPopupList(
      String userUuid,
      String region,
      String district,
      Double latitude,
      Double longitude,
      MapSortStandard mapSortStandard);

  List<PopupUserResponseDto> getRecommendPopupList(String userUuid);

  List<PopupUserResponseDto> getRelatedPopupList(String userUuid, String popupUuid);

  List<PopupUserResponseDto> getRandomPopupList(String userUuid);

  List<PopupUserResponseDto> getRecommendationPopupList(String userUuid, Long recommendId);
}
