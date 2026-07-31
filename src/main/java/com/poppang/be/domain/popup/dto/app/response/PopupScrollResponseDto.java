package com.poppang.be.domain.popup.dto.app.response;

import java.util.List;

public record PopupScrollResponseDto(
    List<PopupScrollItemResponseDto> items, Long nextCursor, boolean hasNext) {}
