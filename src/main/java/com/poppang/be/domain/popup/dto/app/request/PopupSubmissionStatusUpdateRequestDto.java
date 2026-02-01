package com.poppang.be.domain.popup.dto.app.request;

import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupSubmissionStatusUpdateRequestDto {

  private PopupSubmissionStatus popupSubmissionStatus;
}
