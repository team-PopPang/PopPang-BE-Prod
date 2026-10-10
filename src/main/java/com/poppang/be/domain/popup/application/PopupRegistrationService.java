package com.poppang.be.domain.popup.application;

import com.poppang.be.domain.popup.dto.app.request.PopupRegisterRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupRegisterResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupRegistrationService {
  private final PopupRepository popupRepository;
  private final PopupRegistrationWriter writer;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PopupRegisterResponseDto register(PopupRegisterRequestDto request) {
    String postId = request.getInstaPostId();
    Optional<Popup> existing = findExisting(postId);
    if (existing.isPresent()) {
      return new PopupRegisterResponseDto(existing.get().getUuid(), false);
    }
    try {
      Popup popup = writer.register(request);
      return new PopupRegisterResponseDto(popup.getUuid(), true);
    } catch (DataIntegrityViolationException exception) {
      // 사전 조회 이후 다른 요청이 같은 게시물을 저장한 경우, 롤백된 트랜잭션 밖에서 조회한다.
      return findExisting(postId)
          .map(popup -> new PopupRegisterResponseDto(popup.getUuid(), false))
          .orElseThrow(() -> exception);
    }
  }

  private Optional<Popup> findExisting(String postId) {
    return postId == null ? Optional.empty() : popupRepository.findByInstaPostId(postId);
  }
}
