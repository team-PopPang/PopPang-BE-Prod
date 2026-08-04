package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.v2.V2PopupSubmissionCreateRequestDto;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionImage;
import com.poppang.be.domain.popup.entity.PopupSubmissionRecommend;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class V2PopupSubmissionServiceImplTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private UsersRepository usersRepository;
  @Mock private PopupSubmissionRepository popupSubmissionRepository;
  @Mock private PopupSubmissionImageRepository popupSubmissionImageRepository;
  @Mock private PopupSubmissionRecommendRepository popupSubmissionRecommendRepository;
  @Mock private RecommendRepository recommendRepository;
  @Mock private PopupSubmissionImageStorage popupSubmissionImageStorage;

  @InjectMocks private V2PopupSubmissionServiceImpl popupSubmissionService;

  @Test
  void submissionUsesPrincipalUuidAndKeepsLegacyImageAndRecommendOrdering() {
    V2PopupSubmissionCreateRequestDto request = createRequest(List.of(1L, 2L, 1L));
    List<MultipartFile> images = createImages();
    List<String> imageUrls = List.of("/submissionImages/first.jpg", "/submissionImages/second.png");
    Recommend firstRecommend = recommend(1L);
    Recommend secondRecommend = recommend(2L);
    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID))
        .thenReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    when(recommendRepository.findAllById(any()))
        .thenReturn(List.of(secondRecommend, firstRecommend));
    when(popupSubmissionImageStorage.storeAll(images)).thenReturn(imageUrls);
    when(popupSubmissionRepository.saveAndFlush(any(PopupSubmission.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    popupSubmissionService.createPopupSubmission(USER_UUID, request, images);

    ArgumentCaptor<PopupSubmission> submissionCaptor =
        ArgumentCaptor.forClass(PopupSubmission.class);
    verify(popupSubmissionRepository).saveAndFlush(submissionCaptor.capture());
    assertThat(submissionCaptor.getValue().getSubmitterUserUuid()).isEqualTo(USER_UUID);
    assertThat(submissionCaptor.getValue().getStatus()).isEqualTo(PopupSubmissionStatus.PENDING);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PopupSubmissionImage>> imageCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(popupSubmissionImageRepository).saveAllAndFlush(imageCaptor.capture());
    List<PopupSubmissionImage> savedImages =
        StreamSupport.stream(imageCaptor.getValue().spliterator(), false).toList();
    assertThat(savedImages)
        .extracting(PopupSubmissionImage::getImageUrl)
        .containsExactlyElementsOf(imageUrls);
    assertThat(savedImages).extracting(PopupSubmissionImage::getSortOrder).containsExactly(0, 1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PopupSubmissionRecommend>> recommendCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(popupSubmissionRecommendRepository).saveAllAndFlush(recommendCaptor.capture());
    List<PopupSubmissionRecommend> savedRecommends =
        StreamSupport.stream(recommendCaptor.getValue().spliterator(), false).toList();
    assertThat(savedRecommends)
        .extracting(item -> item.getRecommend().getId())
        .containsExactly(1L, 2L);
  }

  @Test
  void nullRequestIsRejectedBeforeUserStorageAndRepositoryAccess() {
    assertThatThrownBy(
            () -> popupSubmissionService.createPopupSubmission(USER_UUID, null, createImages()))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST));

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void missingOrEmptyImagesAreRejectedBeforeUserLookup() {
    V2PopupSubmissionCreateRequestDto request = createRequest(List.of(1L));

    assertThatThrownBy(() -> popupSubmissionService.createPopupSubmission(USER_UUID, request, null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    assertThatThrownBy(
            () -> popupSubmissionService.createPopupSubmission(USER_UUID, request, List.of()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
  }

  @Test
  void blankPrincipalAndNullRecommendIdAreRejectedBeforeUserLookup() {
    assertThatThrownBy(
            () ->
                popupSubmissionService.createPopupSubmission(
                    " ", createRequest(List.of(1L)), createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    assertThatThrownBy(
            () ->
                popupSubmissionService.createPopupSubmission(
                    USER_UUID, createRequest(java.util.Arrays.asList(1L, null)), createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
  }

  @Test
  void deletedOrUnknownPrincipalUserKeepsLegacyUserNotFoundError() {
    V2PopupSubmissionCreateRequestDto request = createRequest(List.of(1L));
    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> popupSubmissionService.createPopupSubmission(USER_UUID, request, createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    verify(recommendRepository, never()).findAllById(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void unknownRecommendIdIsRejectedBeforeImageStorage() {
    V2PopupSubmissionCreateRequestDto request = createRequest(List.of(1L, 2L));
    Recommend recommend = org.mockito.Mockito.mock(Recommend.class);
    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID))
        .thenReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    when(recommendRepository.findAllById(any())).thenReturn(List.of(recommend));

    assertThatThrownBy(
            () -> popupSubmissionService.createPopupSubmission(USER_UUID, request, createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_RECOMMEND_ID);

    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void databaseFailureDeletesOnlyTheImagesStoredForTheFailedSubmission() {
    V2PopupSubmissionCreateRequestDto request = createRequest(List.of(1L));
    List<MultipartFile> images = createImages();
    List<String> imageUrls = List.of("/submissionImages/failed.jpg");
    RuntimeException dbException = new RuntimeException("db failed");
    Recommend recommend = recommend(1L);
    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID))
        .thenReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    when(recommendRepository.findAllById(any())).thenReturn(List.of(recommend));
    when(popupSubmissionImageStorage.storeAll(images)).thenReturn(imageUrls);
    when(popupSubmissionRepository.saveAndFlush(any(PopupSubmission.class))).thenThrow(dbException);

    assertThatThrownBy(
            () -> popupSubmissionService.createPopupSubmission(USER_UUID, request, images))
        .isSameAs(dbException);

    verify(popupSubmissionImageStorage).deleteAll(imageUrls);
  }

  private V2PopupSubmissionCreateRequestDto createRequest(List<Long> recommendIds) {
    V2PopupSubmissionCreateRequestDto request = new V2PopupSubmissionCreateRequestDto();
    ReflectionTestUtils.setField(request, "name", "테스트 팝업");
    ReflectionTestUtils.setField(request, "startDate", LocalDate.of(2026, 8, 1));
    ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 8, 31));
    ReflectionTestUtils.setField(request, "roadAddress", "서울 성동구 왕십리로 123");
    ReflectionTestUtils.setField(request, "region", "서울");
    ReflectionTestUtils.setField(request, "description", "성수 팝업 설명");
    ReflectionTestUtils.setField(request, "recommendIdList", recommendIds);
    return request;
  }

  private List<MultipartFile> createImages() {
    return List.of(
        new MockMultipartFile(
            "images", "first.jpg", "image/jpeg", "first".getBytes(StandardCharsets.UTF_8)),
        new MockMultipartFile(
            "images", "second.png", "image/png", "second".getBytes(StandardCharsets.UTF_8)));
  }

  private Recommend recommend(Long id) {
    Recommend recommend = org.mockito.Mockito.mock(Recommend.class);
    when(recommend.getId()).thenReturn(id);
    return recommend;
  }
}
