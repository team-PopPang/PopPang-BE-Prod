package com.poppang.be.domain.users.application;

import com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordGroupResponseDto;
import com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordResponseDto;
import java.util.List;

public interface V2InternalUsersService {

  List<V2WorkerUserKeywordResponseDto> getUsersWithAlertKeyword();

  List<V2WorkerUserKeywordGroupResponseDto> getUsersWithAlertKeywordGroup();
}
