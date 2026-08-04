package com.poppang.be.domain.users.dto.v2.response;

public record V2WorkerUserKeywordResponseDto(
    String userUuid, String nickname, String fcmToken, String keyword) {}
