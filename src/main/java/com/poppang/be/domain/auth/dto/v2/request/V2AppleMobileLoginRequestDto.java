package com.poppang.be.domain.auth.dto.v2.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record V2AppleMobileLoginRequestDto(
    @JsonProperty("auth_code") String authorizationCode,
    @JsonProperty("raw_nonce") String rawNonce) {}
