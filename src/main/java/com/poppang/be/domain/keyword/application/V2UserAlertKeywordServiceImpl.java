package com.poppang.be.domain.keyword.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.keyword.dto.v2.V2AlertKeywordResponseDto;
import com.poppang.be.domain.keyword.entity.UserAlertKeyword;
import com.poppang.be.domain.keyword.infrastructure.UserAlertKeywordRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2UserAlertKeywordServiceImpl implements V2UserAlertKeywordService {

  private final UserAlertKeywordRepository userAlertKeywordRepository;
  private final UsersRepository usersRepository;

  @Override
  @Transactional(readOnly = true)
  public List<V2AlertKeywordResponseDto> getUserAlertKeywords(String userUuid) {
    return userAlertKeywordRepository.findAllByUserUuid(userUuid).stream()
        .map(keyword -> new V2AlertKeywordResponseDto(keyword.getAlertKeyword()))
        .toList();
  }

  @Override
  @Transactional
  public void registerAlertKeyword(String userUuid, String keyword) {
    requireKeyword(keyword);
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    if (userAlertKeywordRepository.findByUserUuidAndAlertKeyword(userUuid, keyword).isPresent()) {
      throw new BaseException(ErrorCode.ALERT_KEYWORD_ALREADY_EXISTS);
    }
    userAlertKeywordRepository.save(UserAlertKeyword.from(user, keyword));
  }

  @Override
  @Transactional
  public void deleteAlertKeyword(String userUuid, String keyword) {
    requireKeyword(keyword);
    usersRepository
        .findByUuid(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    UserAlertKeyword alertKeyword =
        userAlertKeywordRepository
            .findByUserUuidAndAlertKeyword(userUuid, keyword)
            .orElseThrow(() -> new BaseException(ErrorCode.ALERT_KEYWORD_NOT_FOUND));
    userAlertKeywordRepository.delete(alertKeyword);
  }

  private void requireKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_USER_REQUEST);
    }
  }
}
