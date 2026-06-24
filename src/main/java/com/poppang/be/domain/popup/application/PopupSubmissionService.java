package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;

public interface PopupSubmissionService {

  void createPopupSubmission(PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto);
}
