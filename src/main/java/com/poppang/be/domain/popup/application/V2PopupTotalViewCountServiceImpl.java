package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.v2.V2PopupTotalViewCountResponseDto;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class V2PopupTotalViewCountServiceImpl implements V2PopupTotalViewCountService {

  private static final String REDIS_KEY_PREFIX = "popup:view:";
  private static final String REDIS_KEY_SUFFIX = ":delta";
  private static final Duration TTL = Duration.ofSeconds(70);

  private final RedisTemplate<String, String> redisTemplate;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final PopupCountBoostService popupCountBoostService;

  @Override
  public long increment(String popupUuid) {
    String key = redisKey(popupUuid);
    Long after = redisTemplate.opsForValue().increment(key);
    Long expireSeconds = redisTemplate.getExpire(key);
    if (expireSeconds == null || expireSeconds <= 0) {
      redisTemplate.expire(key, TTL);
    }
    return after == null ? 0L : after;
  }

  @Override
  public long getDelta(String popupUuid) {
    String value = redisTemplate.opsForValue().get(redisKey(popupUuid));
    try {
      return value == null ? 0L : Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return 0L;
    }
  }

  @Override
  public V2PopupTotalViewCountResponseDto getTotalViewCount(String popupUuid) {
    Long persistedCount = popupTotalViewCountRepository.getViewCountByPopupUuid(popupUuid);
    long viewCount = persistedCount == null ? 0L : persistedCount;
    long boost = popupCountBoostService.getViewCountBoostByPopupUuid(popupUuid);
    return new V2PopupTotalViewCountResponseDto(viewCount + boost);
  }

  private String redisKey(String popupUuid) {
    return REDIS_KEY_PREFIX + popupUuid + REDIS_KEY_SUFFIX;
  }
}
