package com.poppang.be.domain.auth.dto.v2.response;

public record V2TokenResponseDto(
    String tokenType,
    String accessToken,
    String refreshToken,
    long accessTokenExpiresIn,
    long refreshTokenExpiresIn) {}
