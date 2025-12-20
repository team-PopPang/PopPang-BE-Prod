package com.poppang.be.domain.keyword.application;

import com.poppang.be.domain.keyword.dto.request.UserAlertKeywordDeleteDto;
import com.poppang.be.domain.keyword.dto.request.UserAlertKeywordRegisterRequestDto;
import com.poppang.be.domain.keyword.dto.response.UserAlertKeywordResponseDto;
import java.util.List;

public interface UserAlertKeywordService {

  List<UserAlertKeywordResponseDto> getUserAlertKeywordList(String userUuid);

  void registerAlertKeyword(UserAlertKeywordRegisterRequestDto userAlertKeywordRegisterRequestDto);

  void deleteAlertKeyword(UserAlertKeywordDeleteDto userAlertKeywordDeleteDto);
}
