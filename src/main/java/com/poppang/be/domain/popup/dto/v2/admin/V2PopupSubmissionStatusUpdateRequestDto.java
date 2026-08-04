package com.poppang.be.domain.popup.dto.v2.admin;

import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class V2PopupSubmissionStatusUpdateRequestDto {

  private PopupSubmissionStatus popupSubmissionStatus;
}
