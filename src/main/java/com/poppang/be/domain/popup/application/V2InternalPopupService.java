package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.internal.V2WorkerPopupImageUpsertRequestDto;
import com.poppang.be.domain.popup.dto.v2.internal.V2WorkerPopupRegisterRequestDto;
import java.util.List;

public interface V2InternalPopupService {

  void registerPopup(V2WorkerPopupRegisterRequestDto request);

  void upsertImages(String popupUuid, List<V2WorkerPopupImageUpsertRequestDto> images);
}
