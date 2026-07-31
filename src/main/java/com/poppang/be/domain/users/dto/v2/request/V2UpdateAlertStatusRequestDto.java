package com.poppang.be.domain.users.dto.v2.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record V2UpdateAlertStatusRequestDto(@JsonProperty("isAlerted") Boolean alerted) {}
