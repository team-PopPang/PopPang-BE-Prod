package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminListResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionStatusUpdateRequestDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface V2PopupAdminService {

  void deactivatePopup(String popupUuid);

  List<V2PopupSubmissionAdminListResponseDto> getPopupSubmissions(String status);

  V2PopupSubmissionAdminDetailResponseDto getPopupSubmissionDetail(Long popupSubmissionId);

  V2PopupSubmissionAdminUpdateResponseDto updatePopupSubmission(
      Long popupSubmissionId,
      V2PopupSubmissionAdminUpdateRequestDto request,
      List<MultipartFile> images);

  void updateSubmissionStatus(Long submissionId, V2PopupSubmissionStatusUpdateRequestDto request);
}
