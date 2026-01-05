package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.response.PopupTotalViewCountResponseDto;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PopupTotalViewCountServiceImpl implements PopupTotalViewCountService {

  private final RedisTemplate<String, String> redisTemplate;
  private static final String PREFIX = "popup:view:";
  private static final String SUFFIX = ":delta";
  private static final Duration TTL = Duration.ofSeconds(70);
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;

  // 조회수 +1 (원자적 INCR)
  @Override
  public long increment(String popupId) {

    String key = PREFIX + popupId + SUFFIX;
    Long after = redisTemplate.opsForValue().increment(key);

    // TTL이 없으면(또는 만료 설정이 사라졌으면) 다시 걸어준다
    Long expireSec = redisTemplate.getExpire(key);
    if (expireSec == null || expireSec <= 0) {
      redisTemplate.expire(key, TTL);
    }

    return after != null ? after : 0L;
  }

  // 현재 1분 누적(delta) 조회 (없으면 0)
  @Override
  public long getDelta(String popupUuid) {
    String key = PREFIX + popupUuid + SUFFIX;
    String v = redisTemplate.opsForValue().get(key);
    try {
      return v != null ? Long.parseLong(v) : 0L;
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  @Override
  public PopupTotalViewCountResponseDto getTotalViewCount(String popupUuid) {
    Long viewCountByPopupUuid = popupTotalViewCountRepository.getViewCountByPopupUuid(popupUuid);

    PopupTotalViewCountResponseDto popupTotalViewCountResponseDto =
        PopupTotalViewCountResponseDto.builder().totalViewCount(viewCountByPopupUuid).build();

    return popupTotalViewCountResponseDto;
  }
}
