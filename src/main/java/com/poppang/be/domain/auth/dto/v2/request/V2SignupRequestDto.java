package com.poppang.be.domain.auth.dto.v2.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record V2SignupRequestDto(
    String nickname,
    @JsonProperty("isAlerted") boolean alerted,
    String fcmToken,
    List<String> alertKeywordList,
    List<Long> recommendList) {}
