package com.poppang.be.domain.users.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.response.UserUpdateFcmTokenResquestDto;
import com.poppang.be.domain.users.dto.request.ChangeNicknameRequestDto;
import com.poppang.be.domain.users.dto.request.UpdateAlertStatusRequestDto;
import com.poppang.be.domain.users.dto.response.*;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

  private final UsersRepository usersRepository;

  @Override
  public NicknameDuplicateResponseDto checkNicknameDuplicated(String nickname) {

    boolean duplicated = usersRepository.existsByNickname(nickname);

    return NicknameDuplicateResponseDto.from(duplicated);
  }

  @Override
  @Transactional
  public void changeNickname(String userUuid, ChangeNicknameRequestDto changeNicknameRequestDto) {

    String nickname = changeNicknameRequestDto.getNickname().trim();

    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    boolean duplicated = usersRepository.existsByNickname(changeNicknameRequestDto.getNickname());
    if (duplicated) {
      throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
    }

    user.changeNickname(changeNicknameRequestDto);
  }

  @Override
  @Transactional
  public void softDeleteUser(String userUuid) {

    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    user.softDelete();
  }

  @Override
  @Transactional
  public void restoreUser(String userUuid) {

    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    user.restore();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isFcmTokenDuplicated(String userUuid, String fcmToken) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    String savedToken = user.getFcmToken();

    if (savedToken == null) {
      return false;
    }
    boolean duplicated = savedToken.equals(fcmToken);

    return duplicated;
  }

  @Override
  @Transactional
  public void updateFcmToken(
      String userUuid, UserUpdateFcmTokenResquestDto userUpdateFcmTokenResquestDto) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    user.updateFcmToken(userUpdateFcmTokenResquestDto.getFcmToken());
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserWithKeywordListResponseDto> getUserWithKeywordList() {
    List<UserWithKeywordListResponseDto> userWithKeywordListResponseDtoList =
        usersRepository.findUserWithAlertKeywordList().stream()
            .map(
                r ->
                    UserWithKeywordListResponseDto.builder()
                        .userId(r.getUserId())
                        .nickname(r.getNickname())
                        .fcmToken(r.getFcmToken())
                        .keyword(r.getKeyword())
                        .build())
            .toList();

    return userWithKeywordListResponseDtoList;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserWithKeywordListResponseDtoB> getUserWithKeywordListB() {
    List<UserWithKeywordListResponseDtoB> userWithKeywordListResponseDtoBList =
        usersRepository.findUserWithAlertKeywordListB().stream()
            .map(
                r ->
                    UserWithKeywordListResponseDtoB.builder()
                        .userId(r.getUserId())
                        .nickname(r.getNickname())
                        .fcmToken(r.getFcmToken())
                        .keywordList(
                            (r.getKeywordList() == null || r.getKeywordList().isBlank())
                                ? List.of()
                                : Arrays.stream(r.getKeywordList().split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isBlank())
                                    .toList())
                        .build())
            .toList();

    return userWithKeywordListResponseDtoBList;
  }

  @Override
  @Transactional(readOnly = true)
  public GetUserResponseDto getUserInfo(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    GetUserResponseDto getUserResponseDto = GetUserResponseDto.from(user);

    return getUserResponseDto;
  }

  @Override
  @Transactional
  public UpdateAlertStatusResponseDto updateAlertStatus(
      String userUuid, UpdateAlertStatusRequestDto updateAlertStatusRequestDto) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    user.updateAlerted(updateAlertStatusRequestDto.isAlerted());

    UpdateAlertStatusResponseDto updateAlertStatusResponseDto =
        UpdateAlertStatusResponseDto.builder().userUuid(userUuid).alerted(user.isAlerted()).build();

    return updateAlertStatusResponseDto;
  }

  @Override
  @Transactional
  public void hardDeleteUser(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    usersRepository.deleteById(user.getId());
  }
}
