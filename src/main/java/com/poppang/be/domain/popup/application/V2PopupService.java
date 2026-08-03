package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.V2PopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2RegionDistrictsResponseDto;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.enums.SortStandard;
import java.util.List;

public interface V2PopupService {

  List<V2PopupResponseDto> getAllPopupList();

  V2PopupResponseDto getPopupByUuid(String popupUuid);

  List<V2PopupResponseDto> getSearchPopupList(String query);

  List<V2PopupResponseDto> getUpcomingPopupList(Integer upcomingDays);

  List<V2PopupResponseDto> getInProgressPopupList();

  List<V2RegionDistrictsResponseDto> getRegionDistricts();

  List<V2PopupResponseDto> getRandomPopupList();

  List<V2PopupResponseDto> getFilteredPopupList(
      String region, String district, SortStandard sortStandard, Double latitude, Double longitude);

  List<V2PopupResponseDto> getFilteredHomePopupList(
      String region, String district, HomeSortStandard homeSortStandard);

  List<V2PopupResponseDto> getFilteredMapPopupList(
      String region,
      String district,
      Double latitude,
      Double longitude,
      MapSortStandard mapSortStandard);

  List<V2PopupResponseDto> getRelatedPopupList(String popupUuid);

  List<V2PopupResponseDto> getRecommendationPopupList(Long recommendId);

  List<V2PopupResponseDto> getRecommendPopupList(String userUuid);
}
