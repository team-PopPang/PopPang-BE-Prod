package com.poppang.be.domain.popup.mapper;

import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PopupWebInProgressResponseDtoMapper {

  private static final int THUMBNAIL_SORT_ORDER = 0;

  private final PopupImageRepository popupImageRepository;

  public List<PopupWebInProgressResponseDto> toResponseDtoList(List<Popup> popupList) {
    if (popupList == null || popupList.isEmpty()) {
      return List.of();
    }

    List<Long> popupIdList = popupList.stream().map(Popup::getId).toList();
    Map<Long, String> thumbnailUrlByPopupId = new LinkedHashMap<>();
    for (PopupImage image :
        popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
            popupIdList, THUMBNAIL_SORT_ORDER)) {
      thumbnailUrlByPopupId.putIfAbsent(image.getPopup().getId(), image.getImageUrl());
    }

    return popupList.stream()
        .map(
            popup ->
                PopupWebInProgressResponseDto.builder()
                    .popupUuid(popup.getUuid())
                    .name(popup.getName())
                    .thumbnailUrl(thumbnailUrlByPopupId.get(popup.getId()))
                    .region(popup.getRegion())
                    .startDate(popup.getStartDate())
                    .endDate(popup.getEndDate())
                    .build())
        .toList();
  }
}
