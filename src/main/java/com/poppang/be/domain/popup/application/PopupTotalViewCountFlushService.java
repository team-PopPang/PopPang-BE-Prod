package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupTotalViewCountFlushService {

  private final RedisTemplate<String, String> redisTemplate;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;

  private static final String PREFIX = "popup:view:";
  private static final String SUFFIX = ":delta";
  private static final long RESET_TTL_MS = 70_000; // TTL이 없으면 최소 60초로 재설정

  @Transactional
  public void flushDeltas() {
    ScanOptions options =
        ScanOptions.scanOptions().match(PREFIX + "*" + SUFFIX).count(2000).build();

    redisTemplate.execute(
        (RedisConnection connection) -> {
          try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            while (cursor.hasNext()) {
              byte[] keyBytes = cursor.next();
              String key = new String(keyBytes, StandardCharsets.UTF_8);
              String uuid = key.substring(PREFIX.length(), key.length() - SUFFIX.length());

              // 현재 TTL(ms) 확보 (없으면 -1, 만료되면 -2)
              Long pttl = connection.keyCommands().pTtl(keyBytes);

              // GETSET key 0 (원자적으로 delta 가져오고 리셋)
              byte[] oldValBytes =
                  connection
                      .stringCommands()
                      .getSet(keyBytes, "0".getBytes(StandardCharsets.UTF_8));

              long delta = 0L;
              if (oldValBytes != null) {
                try {
                  delta = Long.parseLong(new String(oldValBytes, StandardCharsets.UTF_8));
                } catch (NumberFormatException ignored) {
                }
              }

              // TTL 복원/재설정
              if (pttl != null && pttl > 0) {
                connection.keyCommands().pExpire(keyBytes, pttl); // 기존 TTL 유지
              } else {
                connection.keyCommands().pExpire(keyBytes, RESET_TTL_MS); // 최소 TTL 재설정
              }

              // DB 누적
              if (delta > 0) {
                popupTotalViewCountRepository.upsertAdd(uuid, delta);
              }
            }
          }
          return null;
        });
  }
}
