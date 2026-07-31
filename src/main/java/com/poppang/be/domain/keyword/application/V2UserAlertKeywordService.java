package com.poppang.be.domain.keyword.application;

import com.poppang.be.domain.keyword.dto.v2.V2AlertKeywordResponseDto;
import java.util.List;

public interface V2UserAlertKeywordService {

  List<V2AlertKeywordResponseDto> getUserAlertKeywords(String userUuid);

  void registerAlertKeyword(String userUuid, String keyword);

  void deleteAlertKeyword(String userUuid, String keyword);
}
