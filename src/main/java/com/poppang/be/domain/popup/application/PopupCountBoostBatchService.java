package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupCountBoost;
import com.poppang.be.domain.popup.infrastructure.PopupCountBoostRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupCountBoostBatchService {

  static final int MAX_VIEW_COUNT_BOOST_DELTA = 30;
  static final int MAX_FAVORITE_COUNT_BOOST_DELTA = 8;

  private final PopupRepository popupRepository;
  private final PopupCountBoostRepository popupCountBoostRepository;

  @Transactional
  public int boostAllPopupsForToday() {
    return boostAllPopups(LocalDate.now());
  }

  int boostAllPopups(LocalDate boostedDate) {
    List<Popup> popupList = popupRepository.findAll();
    if (popupList.isEmpty()) {
      return 0;
    }

    List<Long> popupIdList = popupList.stream().map(Popup::getId).toList();
    Map<Long, PopupCountBoost> boostMap = new HashMap<>();
    for (PopupCountBoost boost : popupCountBoostRepository.findAllByPopupIdIn(popupIdList)) {
      boostMap.put(boost.getPopupId(), boost);
    }

    List<PopupCountBoost> newBoostList = new ArrayList<>();
    int boostedCount = 0;
    for (Popup popup : popupList) {
      PopupCountBoost boost = boostMap.get(popup.getId());
      if (boost == null) {
        boost = new PopupCountBoost(popup);
        newBoostList.add(boost);
      }

      if (boost.wasBoostedOn(boostedDate)) {
        continue;
      }

      boost.addBoost(nextViewCountBoostDelta(), nextFavoriteCountBoostDelta(), boostedDate);
      boostedCount++;
    }

    if (!newBoostList.isEmpty()) {
      popupCountBoostRepository.saveAll(newBoostList);
    }

    return boostedCount;
  }

  int nextViewCountBoostDelta() {
    return ThreadLocalRandom.current().nextInt(MAX_VIEW_COUNT_BOOST_DELTA + 1);
  }

  int nextFavoriteCountBoostDelta() {
    return ThreadLocalRandom.current().nextInt(MAX_FAVORITE_COUNT_BOOST_DELTA + 1);
  }
}
