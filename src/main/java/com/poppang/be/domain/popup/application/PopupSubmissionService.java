package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface PopupSubmissionService {

  void createPopupSubmission(
      PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto, List<MultipartFile> images);
}
