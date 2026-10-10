package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.alert.entity.UserAlert;
import com.poppang.be.domain.alert.infrastructure.UserAlertRepository;
import com.poppang.be.domain.popup.dto.app.request.PopupAlertTargetRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupAlertTargetResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupAlertTargetResponseDto.PopupSummary;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.projection.PopupAlertTargetRow;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupAlertTargetService {
  private final PopupRepository popupRepository;
  private final UserAlertRepository userAlertRepository;
  private final UsersRepository usersRepository;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public List<PopupAlertTargetResponseDto> findTargets(PopupAlertTargetRequestDto request) {
    if (request == null
        || request.popupUuids() == null
        || request.popupUuids().stream().anyMatch(uuid -> uuid == null || uuid.isBlank())) {
      throw new BaseException(ErrorCode.INVALID_POPUP_ALERT_TARGET_REQUEST);
    }
    List<String> uuids = new ArrayList<>(new LinkedHashSet<>(request.popupUuids()));
    if (uuids.isEmpty()) return List.of();

    // 기존 v1/v2 알림 등록도 같은 팝업 행을 잠근다. 다건 잠금은 항상 ID 순서로 획득한다.
    List<Popup> popups = popupRepository.findAllByUuidInForUpdate(uuids);
    if (popups.size() != uuids.size()) throw new BaseException(ErrorCode.POPUP_NOT_FOUND);
    List<PopupAlertTargetRow> matches = popupRepository.findAlertTargets(uuids);
    if (matches.isEmpty()) return List.of();

    Map<Long, Popup> popupById = popups.stream().collect(Collectors.toMap(Popup::getId, p -> p));
    Map<String, Popup> popupByUuid =
        popups.stream().collect(Collectors.toMap(Popup::getUuid, p -> p));
    List<Long> userIds = matches.stream().map(PopupAlertTargetRow::getUserId).distinct().toList();
    Set<AlertPair> recorded =
        userAlertRepository
            .findExistingPairs(popups.stream().map(Popup::getId).toList(), userIds)
            .stream()
            .map(pair -> new AlertPair(pair.getUserId(), pair.getPopupId()))
            .collect(Collectors.toCollection(HashSet::new));
    Map<Long, List<PopupAlertTargetRow>> byUser = new LinkedHashMap<>();
    List<UserAlert> newAlerts = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (PopupAlertTargetRow row : matches) {
      byUser.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row);
      if (recorded.add(new AlertPair(row.getUserId(), row.getPopupId()))) {
        newAlerts.add(
            UserAlert.builder()
                .user(usersRepository.getReferenceById(row.getUserId()))
                .popup(popupById.get(row.getPopupId()))
                .alertedAt(now)
                .readAt(null)
                .build());
      }
    }
    if (!newAlerts.isEmpty()) userAlertRepository.saveAll(newAlerts);

    List<PopupAlertTargetResponseDto> result = new ArrayList<>();
    for (List<PopupAlertTargetRow> rows : byUser.values()) {
      PopupAlertTargetRow user = rows.get(0);
      if (user.getFcmToken() == null || user.getFcmToken().isBlank()) continue;
      List<String> keywords =
          new ArrayList<>(
              rows.stream()
                  .map(PopupAlertTargetRow::getKeyword)
                  .collect(Collectors.toCollection(LinkedHashSet::new)));
      Set<Long> matchedIds =
          rows.stream().map(PopupAlertTargetRow::getPopupId).collect(Collectors.toSet());
      List<PopupSummary> summaries =
          uuids.stream()
              .map(popupByUuid::get)
              .filter(popup -> matchedIds.contains(popup.getId()))
              .map(popup -> new PopupSummary(popup.getUuid(), popup.getName(), popup.getRegion()))
              .toList();
      result.add(
          new PopupAlertTargetResponseDto(
              user.getUserUuid(), user.getFcmToken(), keywords, summaries));
    }
    return result;
  }

  private record AlertPair(Long userId, Long popupId) {}
}
