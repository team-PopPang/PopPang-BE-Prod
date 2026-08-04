package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import java.util.List;

public interface V2UserPopupService {

  List<V2UserPopupResponseDto> getAllPopupList(String userUuid);

  V2UserPopupResponseDto getPopupByUuid(String userUuid, String popupUuid);

  List<V2UserPopupResponseDto> getUpcomingPopupList(String userUuid, Integer upcomingDays);

  List<V2UserPopupResponseDto> getSearchPopupList(String userUuid, String query);

  List<V2UserPopupResponseDto> getInProgressPopupList(String userUuid);

  List<V2UserPopupResponseDto> getRandomPopupList(String userUuid);

  V2UserPopupScrollResponseDto getScrollPopupList(String userUuid, Long cursor);

  List<V2UserPopupResponseDto> getFilteredHomePopupList(
      String userUuid, String region, String district, HomeSortStandard homeSortStandard);

  List<V2UserPopupResponseDto> getFilteredMapPopupList(
      String userUuid,
      String region,
      String district,
      Double latitude,
      Double longitude,
      MapSortStandard mapSortStandard);

  List<V2UserPopupResponseDto> getRecommendPopupList(String userUuid);

  List<V2UserPopupResponseDto> getRelatedPopupList(String userUuid, String popupUuid);

  List<V2UserPopupResponseDto> getRecommendationPopupList(String userUuid, Long recommendId);
}
