package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.domain.popup.dto.app.request.PopupRegisterRequestDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PopupRegistrationServiceTest {
  @Mock private PopupRepository popupRepository;
  @Mock private PopupRegistrationWriter writer;
  @InjectMocks private PopupRegistrationService service;

  @Test
  void newPopupReturnsPersistedUuidAndCreatedTrue() throws Exception {
    var request = request("post-1");
    when(popupRepository.findByInstaPostId("post-1")).thenReturn(Optional.empty());
    when(writer.register(request)).thenReturn(popup("new-uuid"));

    var result = service.register(request);

    assertThat(result.popupUuid()).isEqualTo("new-uuid");
    assertThat(result.created()).isTrue();
  }

  @Test
  void duplicatePostReturnsExistingUuidWithoutWritingAnyRelatedData() throws Exception {
    var request = request("post-1");
    when(popupRepository.findByInstaPostId("post-1"))
        .thenReturn(Optional.of(popup("existing-uuid")));

    var result = service.register(request);

    assertThat(result.popupUuid()).isEqualTo("existing-uuid");
    assertThat(result.created()).isFalse();
    verifyNoInteractions(writer);
  }

  @Test
  void concurrentUniqueConflictReturnsWinnerAfterWriterRollback() throws Exception {
    var request = request("post-1");
    when(popupRepository.findByInstaPostId("post-1"))
        .thenReturn(Optional.empty(), Optional.of(popup("winner-uuid")));
    when(writer.register(request)).thenThrow(new DataIntegrityViolationException("duplicate"));

    var result = service.register(request);

    assertThat(result.popupUuid()).isEqualTo("winner-uuid");
    assertThat(result.created()).isFalse();
  }

  @Test
  void unrelatedConstraintFailureIsNotReportedAsSuccessfulDuplicate() throws Exception {
    var request = request("post-1");
    var failure = new DataIntegrityViolationException("invalid column");
    when(popupRepository.findByInstaPostId("post-1")).thenReturn(Optional.empty());
    when(writer.register(request)).thenThrow(failure);

    assertThatThrownBy(() -> service.register(request)).isSameAs(failure);
  }

  @Test
  void nullPostIdKeepsExistingRegistrationCompatibility() throws Exception {
    var request = request(null);
    when(writer.register(request)).thenReturn(popup("no-post-id"));

    var result = service.register(request);

    assertThat(result.created()).isTrue();
    assertThat(result.popupUuid()).isEqualTo("no-post-id");
    verifyNoInteractions(popupRepository);
  }

  private PopupRegisterRequestDto request(String postId) throws Exception {
    return new ObjectMapper()
        .readValue(
            "{\"name\":\"수집 팝업\",\"instaPostId\":"
                + (postId == null ? "null" : "\"" + postId + "\"")
                + "}",
            PopupRegisterRequestDto.class);
  }

  private Popup popup(String uuid) {
    return Popup.builder().uuid(uuid).name("기존 이름").build();
  }
}
