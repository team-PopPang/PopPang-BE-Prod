package com.poppang.be.domain.users.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordGroupResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordResponseDto;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2InternalUsersServiceImpl implements V2InternalUsersService {

  private final UsersRepository usersRepository;

  @Override
  @Transactional(readOnly = true)
  public List<V2WorkerUserKeywordResponseDto> getUsersWithAlertKeyword() {
    List<UsersRepository.UserWithKeywordProjection> rows =
        usersRepository.findUserWithAlertKeywordList();
    if (rows.isEmpty()) {
      return List.of();
    }

    Map<Long, String> uuidByUserId =
        loadUuidByUserId(rows.stream().map(row -> row.getUserId()).toList());
    return rows.stream()
        .map(
            row ->
                new V2WorkerUserKeywordResponseDto(
                    requiredUuid(uuidByUserId, row.getUserId()),
                    row.getNickname(),
                    row.getFcmToken(),
                    row.getKeyword()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2WorkerUserKeywordGroupResponseDto> getUsersWithAlertKeywordGroup() {
    List<UsersRepository.UserWithKeywordProjectionB> rows =
        usersRepository.findUserWithAlertKeywordListB();
    if (rows.isEmpty()) {
      return List.of();
    }

    Map<Long, String> uuidByUserId =
        loadUuidByUserId(rows.stream().map(row -> row.getUserId()).toList());
    return rows.stream()
        .map(
            row ->
                new V2WorkerUserKeywordGroupResponseDto(
                    requiredUuid(uuidByUserId, row.getUserId()),
                    row.getNickname(),
                    row.getFcmToken(),
                    splitKeywords(row.getKeywordList())))
        .toList();
  }

  private Map<Long, String> loadUuidByUserId(List<Long> projectedUserIds) {
    List<Long> userIds = projectedUserIds.stream().distinct().toList();
    if (userIds.stream().anyMatch(java.util.Objects::isNull)) {
      throw new BaseException(ErrorCode.INTERNAL_ERROR);
    }

    Map<Long, String> uuidByUserId = new LinkedHashMap<>();
    for (Users user : usersRepository.findAllById(userIds)) {
      if (user.getId() == null || user.getUuid() == null || user.getUuid().isBlank()) {
        throw new BaseException(ErrorCode.INTERNAL_ERROR);
      }
      uuidByUserId.put(user.getId(), user.getUuid());
    }
    if (!uuidByUserId.keySet().containsAll(userIds)) {
      throw new BaseException(ErrorCode.INTERNAL_ERROR);
    }
    return uuidByUserId;
  }

  private String requiredUuid(Map<Long, String> uuidByUserId, Long userId) {
    String userUuid = uuidByUserId.get(userId);
    if (userUuid == null || userUuid.isBlank()) {
      throw new BaseException(ErrorCode.INTERNAL_ERROR);
    }
    return userUuid;
  }

  private List<String> splitKeywords(String keywordList) {
    if (keywordList == null || keywordList.isBlank()) {
      return List.of();
    }
    return Arrays.stream(keywordList.split(","))
        .map(String::trim)
        .filter(keyword -> !keyword.isBlank())
        .toList();
  }
}
