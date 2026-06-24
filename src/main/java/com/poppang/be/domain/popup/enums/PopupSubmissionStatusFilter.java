package com.poppang.be.domain.popup.enums;

import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PopupSubmissionStatusFilter {
  ALL("전체", null),
  PENDING("대기", PopupSubmissionStatus.PENDING),
  APPROVED("승인", PopupSubmissionStatus.APPROVED),
  REJECTED("반려", PopupSubmissionStatus.REJECTED);

  private final String requestValue;
  private final PopupSubmissionStatus status;

  public static Optional<PopupSubmissionStatusFilter> from(String value) {
    if (value == null || value.isBlank()) {
      return Optional.of(ALL);
    }

    String trimmedValue = value.trim();
    return Arrays.stream(values())
        .filter(filter -> filter.requestValue.equals(trimmedValue))
        .findFirst();
  }
}
