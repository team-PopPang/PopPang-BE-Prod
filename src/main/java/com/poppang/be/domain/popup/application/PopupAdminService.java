package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminListResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminUpdateResponseDto;
import java.util.List;

public interface PopupAdminService {

  void deactivatePopup(String userUuid, String popupUuid);

  void deactivatePopupV2(String popupUuid);

  List<PopupSubmissionAdminListResponseDto> getPopupSubmissions(String adminUuid, String status);

  PopupSubmissionAdminDetailResponseDto getPopupSubmissionDetail(
      String adminUuid, Long popupSubmissionId);

  PopupSubmissionAdminUpdateResponseDto updatePopupSubmission(
      String adminUuid,
      Long popupSubmissionId,
      PopupSubmissionAdminUpdateRequestDto popupSubmissionAdminUpdateRequestDto);

  void updateSubmissionStatus(
      Long submissionId,
      PopupSubmissionStatusUpdateRequestDto popupSubmissionStatusUpdateRequestDto);
}
