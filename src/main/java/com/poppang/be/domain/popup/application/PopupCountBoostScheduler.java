package com.poppang.be.domain.popup.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PopupCountBoostScheduler {

  private final PopupCountBoostBatchService popupCountBoostBatchService;

  @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
  public void boostCounts() {
    log.info("[PopupCountBoost] start");
    int boostedCount = popupCountBoostBatchService.boostAllPopupsForToday();
    log.info("[PopupCountBoost] end boostedCount={}", boostedCount);
  }
}
