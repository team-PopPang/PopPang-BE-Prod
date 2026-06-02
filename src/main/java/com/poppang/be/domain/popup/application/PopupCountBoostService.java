package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.infrastructure.PopupCountBoostRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupCountBoostService {

  private final PopupCountBoostRepository popupCountBoostRepository;

  @Transactional(readOnly = true)
  public Map<Long, PopupCountBoostValue> getBoostValueMap(List<Long> popupIds) {
    if (popupIds == null || popupIds.isEmpty()) {
      return Map.of();
    }

    List<Long> distinctPopupIds = popupIds.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctPopupIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, PopupCountBoostValue> boostValueMap = new HashMap<>();
    for (var row : popupCountBoostRepository.findAllBoostValues(distinctPopupIds)) {
      long viewCountBoost = row.getViewCountBoost() == null ? 0L : row.getViewCountBoost();
      long favoriteCountBoost =
          row.getFavoriteCountBoost() == null ? 0L : row.getFavoriteCountBoost();

      boostValueMap.put(
          row.getPopupId(), new PopupCountBoostValue(viewCountBoost, favoriteCountBoost));
    }

    return boostValueMap;
  }

  @Transactional(readOnly = true)
  public PopupCountBoostValue getBoostValue(Long popupId) {
    if (popupId == null) {
      return PopupCountBoostValue.ZERO;
    }

    return popupCountBoostRepository
        .findById(popupId)
        .map(
            boost ->
                new PopupCountBoostValue(boost.getViewCountBoost(), boost.getFavoriteCountBoost()))
        .orElse(PopupCountBoostValue.ZERO);
  }

  @Transactional(readOnly = true)
  public long getViewCountBoostByPopupUuid(String popupUuid) {
    Long boost = popupCountBoostRepository.getViewCountBoostByPopupUuid(popupUuid);
    return boost == null ? 0L : boost;
  }

  @Transactional(readOnly = true)
  public long getFavoriteCountBoostByPopupUuid(String popupUuid) {
    Long boost = popupCountBoostRepository.getFavoriteCountBoostByPopupUuid(popupUuid);
    return boost == null ? 0L : boost;
  }
}
