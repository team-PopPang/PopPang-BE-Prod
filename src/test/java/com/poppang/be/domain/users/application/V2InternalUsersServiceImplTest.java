package com.poppang.be.domain.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2InternalUsersServiceImplTest {

  @Mock private UsersRepository usersRepository;
  @Mock private UsersRepository.UserWithKeywordProjection projectionA1;
  @Mock private UsersRepository.UserWithKeywordProjection projectionA2;
  @Mock private UsersRepository.UserWithKeywordProjectionB projectionB;

  @InjectMocks private V2InternalUsersServiceImpl usersService;

  @Test
  void pollingAUsesOneBatchLookupAndReturnsUuidWithoutChangingLegacyOrder() {
    when(projectionA1.getUserId()).thenReturn(2L);
    when(projectionA1.getNickname()).thenReturn("둘");
    when(projectionA1.getFcmToken()).thenReturn("token-2");
    when(projectionA1.getKeyword()).thenReturn("캐릭터");
    when(projectionA2.getUserId()).thenReturn(2L);
    when(projectionA2.getNickname()).thenReturn("둘");
    when(projectionA2.getFcmToken()).thenReturn("token-2");
    when(projectionA2.getKeyword()).thenReturn("패션");
    when(usersRepository.findUserWithAlertKeywordList())
        .thenReturn(List.of(projectionA1, projectionA2));
    when(usersRepository.findAllById(List.of(2L)))
        .thenReturn(List.of(Users.builder().id(2L).uuid("uuid-2").build()));

    var responses = usersService.getUsersWithAlertKeyword();

    assertThat(responses)
        .extracting("userUuid", "nickname", "fcmToken", "keyword")
        .containsExactly(
            tuple("uuid-2", "둘", "token-2", "캐릭터"), tuple("uuid-2", "둘", "token-2", "패션"));
    verify(usersRepository).findAllById(List.of(2L));
  }

  @Test
  void pollingBSplitsLegacyGroupConcatAndReturnsUuid() {
    when(projectionB.getUserId()).thenReturn(4L);
    when(projectionB.getNickname()).thenReturn("넷");
    when(projectionB.getFcmToken()).thenReturn("token-4");
    when(projectionB.getKeywordList()).thenReturn("굿즈, 캐릭터");
    when(usersRepository.findUserWithAlertKeywordListB()).thenReturn(List.of(projectionB));
    when(usersRepository.findAllById(List.of(4L)))
        .thenReturn(List.of(Users.builder().id(4L).uuid("uuid-4").build()));

    var responses = usersService.getUsersWithAlertKeywordGroup();

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).userUuid()).isEqualTo("uuid-4");
    assertThat(responses.get(0).keywordList()).containsExactly("굿즈", "캐릭터");
  }

  @Test
  void emptyPollingResultDoesNotRunAnUnnecessaryBatchLookup() {
    when(usersRepository.findUserWithAlertKeywordList()).thenReturn(List.of());

    assertThat(usersService.getUsersWithAlertKeyword()).isEmpty();

    verify(usersRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void missingOrBlankUuidStopsTheWorkerResponseInsteadOfExposingInvalidIdentity() {
    when(projectionA1.getUserId()).thenReturn(8L);
    when(usersRepository.findUserWithAlertKeywordList()).thenReturn(List.of(projectionA1));
    when(usersRepository.findAllById(List.of(8L)))
        .thenReturn(List.of(Users.builder().id(8L).uuid(" ").build()));

    assertThatThrownBy(() -> usersService.getUsersWithAlertKeyword())
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void missingBatchUserStopsTheWorkerResponse() {
    when(projectionB.getUserId()).thenReturn(9L);
    when(usersRepository.findUserWithAlertKeywordListB()).thenReturn(List.of(projectionB));
    when(usersRepository.findAllById(List.of(9L))).thenReturn(List.of());

    assertThatThrownBy(() -> usersService.getUsersWithAlertKeywordGroup())
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  private org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.groups.Tuple.tuple(values);
  }
}
