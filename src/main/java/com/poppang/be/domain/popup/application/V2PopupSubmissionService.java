package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.V2PopupSubmissionCreateRequestDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface V2PopupSubmissionService {

  void createPopupSubmission(
      String userUuid,
      V2PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto,
      List<MultipartFile> images);
}
