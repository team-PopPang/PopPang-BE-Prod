package com.poppang.be.domain.popup.dto.v2;

import java.util.List;

public record V2UserPopupScrollResponseDto(
    List<V2UserPopupScrollItemResponseDto> items, Long nextCursor, boolean hasNext) {}
