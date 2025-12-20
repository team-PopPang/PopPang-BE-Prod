package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.request.PopupImageUpsertRequestDto;
import java.util.List;

public interface PopupImageService {

  void upsertImages(
      String popupUuid, List<PopupImageUpsertRequestDto> popupImageUpsertRequestDtoList);
}
