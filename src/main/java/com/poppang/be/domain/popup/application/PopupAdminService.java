package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopPopupSubmissionResponseDto;
import java.util.List;

public interface PopupAdminService {

  void deactivatePopup(String userUuid, String popupUuid);

  void deactivatePopupV2(String popupUuid);

  void createPopupSubmission(PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto);

  List<PopPopupSubmissionResponseDto> getPendingSubmissions();

  void updateSubmissionStatus(
      Long submissionId,
      PopupSubmissionStatusUpdateRequestDto popupSubmissionStatusUpdateRequestDto);
}
