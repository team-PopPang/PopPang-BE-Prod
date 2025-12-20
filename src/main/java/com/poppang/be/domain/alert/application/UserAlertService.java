package com.poppang.be.domain.alert.application;

import com.poppang.be.domain.alert.dto.request.UserAlertDeleteRequestDto;
import com.poppang.be.domain.alert.dto.request.UserAlertRegisterRequestDto;
import com.poppang.be.domain.alert.dto.response.UserAlertResponseDto;
import java.util.List;

public interface UserAlertService {

  void registerUserAlert(String userUuid, UserAlertRegisterRequestDto userAlertRegisterRequestDto);

  void deleteUserAlert(String userUuid, UserAlertDeleteRequestDto userAlertDeleteRequestDto);

  List<UserAlertResponseDto> getUserAlertPopupList(String userUuid);

  void readUserAlertPopup(String userUuid, String popupUuid);
}
