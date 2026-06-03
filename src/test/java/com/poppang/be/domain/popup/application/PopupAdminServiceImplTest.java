package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PopupAdminServiceImplTest {

  private static final String SUBMITTER_USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private UsersRepository usersRepository;

  @Mock private PopupRepository popupRepository;

  @Mock private PopupSubmissionRepository popupSubmissionRepository;

  @InjectMocks private PopupAdminServiceImpl popupAdminService;

  @Test
  void createPopupSubmissionSavesSubmissionWithSubmitterUserUuid() {
    PopupSubmissionCreateRequestDto request = createRequest(SUBMITTER_USER_UUID);
    Users submitter = Users.builder().uuid(SUBMITTER_USER_UUID).build();

    when(usersRepository.findByUuidAndDeletedFalse(SUBMITTER_USER_UUID))
        .thenReturn(Optional.of(submitter));

    popupAdminService.createPopupSubmission(request);

    ArgumentCaptor<PopupSubmission> captor = ArgumentCaptor.forClass(PopupSubmission.class);
    verify(popupSubmissionRepository).save(captor.capture());

    PopupSubmission savedSubmission = captor.getValue();
    assertThat(savedSubmission.getName()).isEqualTo("테스트 팝업");
    assertThat(savedSubmission.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 3));
    assertThat(savedSubmission.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(savedSubmission.getAddress()).isEqualTo("테스트 주소");
    assertThat(savedSubmission.getDescription()).isEqualTo("test body");
    assertThat(savedSubmission.getSubmitterUserUuid()).isEqualTo(SUBMITTER_USER_UUID);
    assertThat(savedSubmission.getStatus()).isEqualTo(PopupSubmissionStatus.PENDING);
  }

  @Test
  void createPopupSubmissionThrowsWhenSubmitterUserUuidIsBlank() {
    PopupSubmissionCreateRequestDto request = createRequest(" ");

    assertThatThrownBy(() -> popupAdminService.createPopupSubmission(request))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_SUBMITTER_USER_UUID);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionRepository, never()).save(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenSubmitterUserUuidIsNull() {
    PopupSubmissionCreateRequestDto request = createRequest(null);

    assertThatThrownBy(() -> popupAdminService.createPopupSubmission(request))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_SUBMITTER_USER_UUID);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionRepository, never()).save(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenRequestIsNull() {
    assertThatThrownBy(() -> popupAdminService.createPopupSubmission(null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_SUBMITTER_USER_UUID);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionRepository, never()).save(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenSubmitterUserUuidDoesNotExist() {
    PopupSubmissionCreateRequestDto request = createRequest(SUBMITTER_USER_UUID);

    when(usersRepository.findByUuidAndDeletedFalse(SUBMITTER_USER_UUID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> popupAdminService.createPopupSubmission(request))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    verify(popupSubmissionRepository, never()).save(any());
  }

  private PopupSubmissionCreateRequestDto createRequest(String submitterUserUuid) {
    PopupSubmissionCreateRequestDto request = new PopupSubmissionCreateRequestDto();
    ReflectionTestUtils.setField(request, "name", "테스트 팝업");
    ReflectionTestUtils.setField(request, "startDate", LocalDate.of(2026, 6, 3));
    ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 6, 10));
    ReflectionTestUtils.setField(request, "address", "테스트 주소");
    ReflectionTestUtils.setField(request, "description", "test body");
    ReflectionTestUtils.setField(request, "submitterUserUuid", submitterUserUuid);
    return request;
  }
}
