package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopPopupSubmissionResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRepository;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupAdminServiceImpl implements PopupAdminService {

  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;
  private final PopupSubmissionRepository popupSubmissionRepository;

  @Override
  @Transactional
  public void deactivatePopup(String userUuid, String popupUuid) {

    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    if (user.getRole() != Role.ADMIN) {
      throw new BaseException(ErrorCode.ACCESS_DENIED);
    }

    popup.deactivate();
  }

  @Override
  @Transactional
  public void deactivatePopupV2(String popupUuid) {
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    popup.deactivate();
  }

  @Override
  public void createPopupSubmission(
      PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto) {
    PopupSubmission popupSubmission = popupSubmissionCreateRequestDto.toEntity();
    popupSubmissionRepository.save(popupSubmission);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopPopupSubmissionResponseDto> getPendingSubmissions() {
    List<PopupSubmission> popupSubmissionList =
        popupSubmissionRepository.findByStatus(PopupSubmissionStatus.PENDING);

    List<PopPopupSubmissionResponseDto> popupSubmissionResponseDtoList =
        popupSubmissionList.stream().map(PopPopupSubmissionResponseDto::from).toList();

    return popupSubmissionResponseDtoList;
  }

  @Override
  @Transactional
  public void updateSubmissionStatus(
      Long submissionId,
      PopupSubmissionStatusUpdateRequestDto popupSubmissionStatusUpdateRequestDto) {
    PopupSubmission popupSubmission =
        popupSubmissionRepository
            .findById(submissionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    if (popupSubmission.getStatus() != PopupSubmissionStatus.PENDING) {
      throw new BaseException(ErrorCode.FAVORITE_ALREADY_EXISTS);
    }
    popupSubmission.updateStatus(popupSubmissionStatusUpdateRequestDto.getPopupSubmissionStatus());
  }
}
