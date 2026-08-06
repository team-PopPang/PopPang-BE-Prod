package com.poppang.be.domain.users.dto.v2.response;

import java.util.List;

public record V2WorkerUserKeywordGroupResponseDto(
    String userUuid, String nickname, String fcmToken, List<String> keywordList) {}
