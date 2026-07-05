package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopupAdminServiceImplTest {

  @Mock private UsersRepository usersRepository;

  @InjectMocks private PopupAdminServiceImpl popupAdminService;

  @Test
  void getPopupSubmissionsThrowsWhenAdminUuidIsBlank() {
    assertThatThrownBy(() -> popupAdminService.getPopupSubmissions(" ", "PENDING"))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_ADMIN_USER_UUID);

    verifyNoInteractions(usersRepository);
  }
}
