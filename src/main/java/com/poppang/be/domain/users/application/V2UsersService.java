package com.poppang.be.domain.users.application;

import com.poppang.be.domain.users.dto.v2.request.V2ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateFcmTokenRequestDto;
import com.poppang.be.domain.users.dto.v2.response.V2NicknameDuplicateResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UpdateAlertStatusResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UserResponseDto;

public interface V2UsersService {

  V2UserResponseDto getUser(String userUuid);

  V2UpdateAlertStatusResponseDto updateAlertStatus(
      String userUuid, V2UpdateAlertStatusRequestDto request);

  V2NicknameDuplicateResponseDto checkNicknameDuplicated(String userUuid, String nickname);

  void changeNickname(String userUuid, V2ChangeNicknameRequestDto request);

  void softDelete(String userUuid);

  void updateFcmToken(String userUuid, V2UpdateFcmTokenRequestDto request);
}
