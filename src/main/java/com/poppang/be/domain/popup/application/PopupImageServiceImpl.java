package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.request.PopupImageUpsertRequestDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupImageServiceImpl implements PopupImageService {

  private final PopupImageRepository popupImageRepository;
  private final PopupRepository popupRepository;

  @Override
  @Transactional
  public void upsertImages(
      String popupUuid, List<PopupImageUpsertRequestDto> popupImageUpsertRequestDtoList) {

    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    popupImageRepository.deleteByPopup_Id(popup.getId());

    List<PopupImage> newImages = new ArrayList<>();
    for (int i = 0; i < popupImageUpsertRequestDtoList.size(); i++) {
      PopupImageUpsertRequestDto dto = popupImageUpsertRequestDtoList.get(i);
      newImages.add(
          PopupImage.builder()
              .popup(popup)
              .imageUrl(dto.getImageUrl())
              .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i)
              .build());
    }

    popupImageRepository.saveAll(newImages);
  }
}
