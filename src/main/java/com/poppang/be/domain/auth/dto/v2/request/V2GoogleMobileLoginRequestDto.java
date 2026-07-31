package com.poppang.be.domain.auth.dto.v2.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record V2GoogleMobileLoginRequestDto(@JsonProperty("id_token") String idToken) {}
