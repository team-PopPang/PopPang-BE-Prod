package com.poppang.be.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret, long accessTokenExpMinutes, long refreshTokenExpDays, String issuer) {}
