package com.poppang.be.domain.popup.mapper;

import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebInProgressResponseDto;
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
public class V2PopupWebInProgressResponseDtoMapper {

  private static final int THUMBNAIL_SORT_ORDER = 0;

  private final PopupImageRepository popupImageRepository;

  public List<V2PopupWebInProgressResponseDto> toResponseDtoList(List<Popup> popupList) {
    if (popupList == null || popupList.isEmpty()) {
      return List.of();
    }

    List<Long> popupIds = popupList.stream().map(Popup::getId).toList();
    Map<Long, String> thumbnailByPopupId = new LinkedHashMap<>();
    for (PopupImage image :
        popupImageRepository.findAllByPopup_IdInAndSortOrderOrderByPopup_IdAscIdAsc(
            popupIds, THUMBNAIL_SORT_ORDER)) {
      thumbnailByPopupId.putIfAbsent(image.getPopup().getId(), image.getImageUrl());
    }

    return popupList.stream()
        .map(
            popup ->
                new V2PopupWebInProgressResponseDto(
                    popup.getUuid(),
                    popup.getName(),
                    thumbnailByPopupId.get(popup.getId()),
                    popup.getRegion(),
                    popup.getStartDate(),
                    popup.getEndDate()))
        .toList();
  }
}
