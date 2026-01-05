package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.request.PopupRegisterRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupResponseDto;
import com.poppang.be.domain.popup.dto.app.response.RegionDistrictsResponse;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.enums.SortStandard;
import java.util.List;

public interface PopupService {

  List<PopupResponseDto> getAllPopupList();

  List<PopupResponseDto> getSearchPopupList(String q);

  List<PopupResponseDto> getUpcomingPopupList(Integer upcomingDays);

  List<PopupResponseDto> getInProgressPopupList();

  List<RegionDistrictsResponse> getRegionDistricts();

  List<PopupResponseDto> getFilteredPopupList(
      String region, String district, SortStandard sortStandard, Double latitude, Double longitude);

  PopupResponseDto getPopupByUuid(String popupUuid);

  List<PopupResponseDto> getFilteredHomePopupList(
      String region, String district, HomeSortStandard homeSortStandard);

  List<PopupResponseDto> getFilteredMapPopupList(
      String region,
      String district,
      Double latitude,
      Double longitude,
      MapSortStandard mapSortStandard);

  void registerPopup(PopupRegisterRequestDto popupRegisterRequestDto);

  List<PopupResponseDto> getRecommendPopupList(String userUuid);

  List<PopupResponseDto> getRelatedPopupList(String popupUuid);

  List<PopupResponseDto> getRandomPopupList();
}
