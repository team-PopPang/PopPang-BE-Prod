package com.poppang.be.domain.popup.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PopupTotalViewCountFlushScheduler {

  private final PopupTotalViewCountFlushService popupTotalViewCountFlushService;

  @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
  public void flush() {
    log.info("[Flush] start");
    popupTotalViewCountFlushService.flushDeltas();
    log.info("[Flush] end");
  }
}
