package com.poppang.be.domain.popup.dto.app.response;

import java.util.List;

public record PopupAlertTargetResponseDto(
    String userUuid, String fcmToken, List<String> keywords, List<PopupSummary> popups) {
  public record PopupSummary(String popupUuid, String name, String region) {}
}
