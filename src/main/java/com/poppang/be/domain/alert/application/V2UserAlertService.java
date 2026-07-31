package com.poppang.be.domain.alert.application;

import com.poppang.be.domain.alert.dto.v2.V2UserAlertResponseDto;
import java.util.List;

public interface V2UserAlertService {

  List<V2UserAlertResponseDto> getUserAlertPopupList(String userUuid);

  void deleteUserAlert(String userUuid, String popupUuid);

  void readUserAlertPopup(String userUuid, String popupUuid);
}
