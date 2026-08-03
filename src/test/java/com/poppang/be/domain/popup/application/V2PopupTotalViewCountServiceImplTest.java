package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.domain.popup.dto.v2.V2PopupTotalViewCountResponseDto;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class V2PopupTotalViewCountServiceImplTest {

  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";
  private static final String REDIS_KEY = "popup:view:" + POPUP_UUID + ":delta";

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private PopupTotalViewCountRepository popupTotalViewCountRepository;
  @Mock private PopupCountBoostService popupCountBoostService;

  private V2PopupTotalViewCountServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new V2PopupTotalViewCountServiceImpl(
            redisTemplate, popupTotalViewCountRepository, popupCountBoostService);
  }

  @Test
  void incrementUsesAtomicRedisIncrementAndRestoresTheSeventySecondTtl() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.increment(REDIS_KEY)).willReturn(4L);
    given(redisTemplate.getExpire(REDIS_KEY)).willReturn(-1L);

    assertThat(service.increment(POPUP_UUID)).isEqualTo(4L);

    verify(valueOperations).increment(REDIS_KEY);
    verify(redisTemplate).expire(REDIS_KEY, Duration.ofSeconds(70));
  }

  @Test
  void incrementKeepsAnExistingPositiveTtl() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.increment(REDIS_KEY)).willReturn(5L);
    given(redisTemplate.getExpire(REDIS_KEY)).willReturn(30L);

    assertThat(service.increment(POPUP_UUID)).isEqualTo(5L);

    verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  void deltaReturnsTheStoredNumberAndFallsBackToZeroForMissingOrInvalidValues() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get(REDIS_KEY)).willReturn("7", null, "not-a-number");

    assertThat(service.getDelta(POPUP_UUID)).isEqualTo(7L);
    assertThat(service.getDelta(POPUP_UUID)).isZero();
    assertThat(service.getDelta(POPUP_UUID)).isZero();
  }

  @Test
  void totalViewCountAddsThePersistedCountAndConfiguredBoostWithoutRedisDelta() {
    given(popupTotalViewCountRepository.getViewCountByPopupUuid(POPUP_UUID)).willReturn(10L);
    given(popupCountBoostService.getViewCountBoostByPopupUuid(POPUP_UUID)).willReturn(3L);

    V2PopupTotalViewCountResponseDto response = service.getTotalViewCount(POPUP_UUID);

    assertThat(response.totalViewCount()).isEqualTo(13L);
    verify(redisTemplate, never()).opsForValue();
  }
}
