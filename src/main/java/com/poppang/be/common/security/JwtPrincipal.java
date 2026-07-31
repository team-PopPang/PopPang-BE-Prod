package com.poppang.be.common.security;

import com.poppang.be.common.jwt.JwtTokenType;

public record JwtPrincipal(String userUuid, JwtTokenType tokenType, String sessionId) {}
