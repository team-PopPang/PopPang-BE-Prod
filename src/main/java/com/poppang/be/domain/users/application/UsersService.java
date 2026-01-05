package com.poppang.be.domain.users.application;

import com.poppang.be.domain.popup.dto.app.response.UserUpdateFcmTokenResquestDto;
import com.poppang.be.domain.users.dto.request.ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.request.UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.response.*;
import java.util.List;

public interface UsersService {

  NicknameDuplicateResponseDto checkNicknameDuplicated(String nickname);

  void changeNickname(String userUuid, ChangeNicknameRequestDto changeNicknameRequestDto);

  void softDeleteUser(String userUuid);

  void restoreUser(String userUuid);

  boolean isFcmTokenDuplicated(String userUuid, String fcmToken);

  void updateFcmToken(String userUuid, UserUpdateFcmTokenResquestDto userUpdateFcmTokenResquestDto);

  List<UserWithKeywordListResponseDto> getUserWithKeywordList();

  List<UserWithKeywordListResponseDtoB> getUserWithKeywordListB();

  GetUserResponseDto getUserInfo(String userUuid);

  UpdateAlertStatusResponseDto updateAlertStatus(
      String userUuid, UpdateAlertStatusRequestDto updateAlertStatusRequestDto);

  void hardDeleteUser(String userUuid);
}
