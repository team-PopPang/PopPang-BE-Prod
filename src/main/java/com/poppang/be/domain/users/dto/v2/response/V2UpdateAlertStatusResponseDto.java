package com.poppang.be.domain.users.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record V2UpdateAlertStatusResponseDto(
    String userUuid, @JsonProperty("isAlerted") boolean alerted) {}
