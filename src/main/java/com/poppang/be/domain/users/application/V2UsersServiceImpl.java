package com.poppang.be.domain.users.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.dto.v2.request.V2ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.v2.request.V2UpdateFcmTokenRequestDto;
import com.poppang.be.domain.users.dto.v2.response.V2NicknameDuplicateResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UpdateAlertStatusResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2UserResponseDto;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2UsersServiceImpl implements V2UsersService {

  private final UsersRepository usersRepository;
  private final V2UsersDeactivationWriter deactivationWriter;
  private final V2UserTokenCleanupService tokenCleanupService;

  @Override
  @Transactional(readOnly = true)
  public V2UserResponseDto getUser(String userUuid) {
    return V2UserResponseDto.from(findActiveCompletedUser(userUuid));
  }

  @Override
  @Transactional
  public V2UpdateAlertStatusResponseDto updateAlertStatus(
      String userUuid, V2UpdateAlertStatusRequestDto request) {
    if (request == null || request.alerted() == null) {
      throw invalidRequest();
    }
    Users user = findActiveCompletedUser(userUuid);
    user.updateAlerted(request.alerted());
    return new V2UpdateAlertStatusResponseDto(userUuid, user.isAlerted());
  }

  @Override
  @Transactional(readOnly = true)
  public V2NicknameDuplicateResponseDto checkNicknameDuplicated(String userUuid, String nickname) {
    String normalizedNickname = normalizeNickname(nickname);
    findActiveCompletedUser(userUuid);
    return new V2NicknameDuplicateResponseDto(usersRepository.existsByNickname(normalizedNickname));
  }

  @Override
  @Transactional
  public void changeNickname(String userUuid, V2ChangeNicknameRequestDto request) {
    String normalizedNickname = normalizeNickname(request == null ? null : request.getNickname());
    Users user = findActiveCompletedUser(userUuid);
    if (usersRepository.existsByNickname(normalizedNickname)) {
      throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
    }
    user.changeNickname(new V2ChangeNicknameRequestDto(normalizedNickname));
  }

  @Override
  public void softDelete(String userUuid) {
    deactivationWriter.softDelete(userUuid);
    tokenCleanupService.cleanup(userUuid);
  }

  @Override
  @Transactional
  public void updateFcmToken(String userUuid, V2UpdateFcmTokenRequestDto request) {
    if (request == null || request.fcmToken() == null || request.fcmToken().isBlank()) {
      throw invalidRequest();
    }
    Users user = findActiveCompletedUser(userUuid);
    user.updateFcmToken(request.fcmToken());
  }

  private Users findActiveCompletedUser(String userUuid) {
    if (userUuid == null || userUuid.isBlank()) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return usersRepository
        .findByUuidAndDeletedFalseAndSignupStatus(userUuid, SignupStatus.COMPLETED)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
  }

  private String normalizeNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw invalidRequest();
    }
    return nickname.trim();
  }

  private BaseException invalidRequest() {
    return new BaseException(ErrorCode.INVALID_USER_REQUEST);
  }
}
