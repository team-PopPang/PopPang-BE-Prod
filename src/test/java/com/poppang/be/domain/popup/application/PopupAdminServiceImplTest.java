package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionAdminImageRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminUpdateResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
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
class PopupAdminServiceImplTest {

  private static final String ADMIN_UUID = "admin-uuid";
  private static final Long POPUP_SUBMISSION_ID = 15L;
  private static final String POPUP_UUID = "popup-uuid";

  @Mock private UsersRepository usersRepository;

  @Mock private PopupRepository popupRepository;

  @Mock private PopupImageRepository popupImageRepository;

  @Mock private PopupRecommendRepository popupRecommendRepository;

  @Mock private PopupSubmissionRepository popupSubmissionRepository;

  @Mock private PopupSubmissionImageRepository popupSubmissionImageRepository;

  @Mock private PopupSubmissionRecommendRepository popupSubmissionRecommendRepository;

  @Mock private RecommendRepository recommendRepository;

  @Mock private PopupSubmissionImageStorage popupSubmissionImageStorage;

  @InjectMocks private PopupAdminServiceImpl popupAdminService;

  @Test
  void getPopupSubmissionsThrowsWhenAdminUuidIsBlank() {
    assertThatThrownBy(() -> popupAdminService.getPopupSubmissions(" ", "PENDING"))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_ADMIN_USER_UUID);

    verifyNoInteractions(usersRepository);
  }

  @Test
  void updatePopupSubmissionRejectsWithoutImages() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    mockAdminAndPopupSubmission(popupSubmission);

    PopupSubmissionAdminUpdateResponseDto response =
        popupAdminService.updatePopupSubmission(
            ADMIN_UUID, POPUP_SUBMISSION_ID, rejectedRequest(), null);

    assertThat(response.getPopupUuid()).isNull();
    assertThat(popupSubmission.getStatus()).isEqualTo(PopupSubmissionStatus.REJECTED);
    verifyNoInteractions(
        popupRepository, popupImageRepository, popupRecommendRepository, recommendRepository);
    verifyNoInteractions(popupSubmissionImageStorage);
  }

  @Test
  void updatePopupSubmissionApprovesWithExistingImagesOnly() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    mockAdminAndPopupSubmission(popupSubmission);
    mockRecommendList();
    mockPopupSave();

    PopupSubmissionAdminUpdateResponseDto response =
        popupAdminService.updatePopupSubmission(
            ADMIN_UUID,
            POPUP_SUBMISSION_ID,
            approvalRequest(List.of(existingImage("/submissionImages/2026/07/existing.jpg", 0))),
            null);

    assertThat(response.getPopupUuid()).isEqualTo(POPUP_UUID);
    assertThat(popupSubmission.getStatus()).isEqualTo(PopupSubmissionStatus.APPROVED);
    verify(popupSubmissionImageStorage, never()).storeAll(any());

    List<PopupImage> savedImages = captureSavedPopupImages();
    assertThat(savedImages).hasSize(1);
    assertThat(savedImages.get(0).getImageUrl())
        .isEqualTo("/submissionImages/2026/07/existing.jpg");
    assertThat(savedImages.get(0).getSortOrder()).isZero();
  }

  @Test
  void updatePopupSubmissionApprovesWithUploadImagesOnly() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    MockMultipartFile uploadImage = imageFile("new-image.jpg", "image/jpeg");
    List<String> storedImageUrls =
        List.of("/submissionImages/2026/07/11111111-1111-1111-1111-111111111111.jpg");
    mockAdminAndPopupSubmission(popupSubmission);
    mockRecommendList();
    mockPopupSave();
    when(popupSubmissionImageStorage.storeAll(any())).thenReturn(storedImageUrls);

    popupAdminService.updatePopupSubmission(
        ADMIN_UUID,
        POPUP_SUBMISSION_ID,
        approvalRequest(List.of(uploadImage(0, 0))),
        List.of(uploadImage));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MultipartFile>> uploadCaptor = ArgumentCaptor.forClass(List.class);
    verify(popupSubmissionImageStorage).storeAll(uploadCaptor.capture());
    assertThat(uploadCaptor.getValue()).containsExactly(uploadImage);

    List<PopupImage> savedImages = captureSavedPopupImages();
    assertThat(savedImages).hasSize(1);
    assertThat(savedImages.get(0).getImageUrl()).isEqualTo(storedImageUrls.get(0));
    assertThat(savedImages.get(0).getSortOrder()).isZero();
  }

  @Test
  void updatePopupSubmissionApprovesWithExistingAndUploadImagesSortedBySortOrder() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    MockMultipartFile uploadImage = imageFile("new-image.png", "image/png");
    List<String> storedImageUrls =
        List.of("/submissionImages/2026/07/22222222-2222-2222-2222-222222222222.png");
    mockAdminAndPopupSubmission(popupSubmission);
    mockRecommendList();
    mockPopupSave();
    when(popupSubmissionImageStorage.storeAll(any())).thenReturn(storedImageUrls);

    popupAdminService.updatePopupSubmission(
        ADMIN_UUID,
        POPUP_SUBMISSION_ID,
        approvalRequest(
            List.of(uploadImage(0, 1), existingImage("/submissionImages/2026/07/existing.jpg", 0))),
        List.of(uploadImage));

    List<PopupImage> savedImages = captureSavedPopupImages();
    assertThat(savedImages).hasSize(2);
    assertThat(savedImages.get(0).getImageUrl())
        .isEqualTo("/submissionImages/2026/07/existing.jpg");
    assertThat(savedImages.get(0).getSortOrder()).isZero();
    assertThat(savedImages.get(1).getImageUrl()).isEqualTo(storedImageUrls.get(0));
    assertThat(savedImages.get(1).getSortOrder()).isEqualTo(1);
  }

  @Test
  void updatePopupSubmissionApprovesWithNullSortOrderUsingImageListOrder() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    mockAdminAndPopupSubmission(popupSubmission);
    mockRecommendList();
    mockPopupSave();

    popupAdminService.updatePopupSubmission(
        ADMIN_UUID,
        POPUP_SUBMISSION_ID,
        approvalRequest(
            List.of(
                existingImage("/submissionImages/2026/07/first.jpg", null),
                existingImage("/submissionImages/2026/07/second.jpg", null))),
        null);

    List<PopupImage> savedImages = captureSavedPopupImages();
    assertThat(savedImages).hasSize(2);
    assertThat(savedImages.get(0).getSortOrder()).isZero();
    assertThat(savedImages.get(1).getSortOrder()).isEqualTo(1);
  }

  @Test
  void updatePopupSubmissionThrowsWhenApprovedImageListIsEmpty() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    mockAdminAndPopupSubmission(popupSubmission);

    assertThatThrownBy(
            () ->
                popupAdminService.updatePopupSubmission(
                    ADMIN_UUID, POPUP_SUBMISSION_ID, approvalRequest(List.of()), null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(recommendRepository, never()).findAllById(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void updatePopupSubmissionThrowsWhenUploadImageHasNoImagesPart() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    mockAdminAndPopupSubmission(popupSubmission);

    assertThatThrownBy(
            () ->
                popupAdminService.updatePopupSubmission(
                    ADMIN_UUID,
                    POPUP_SUBMISSION_ID,
                    approvalRequest(List.of(uploadImage(0, 0))),
                    null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(recommendRepository, never()).findAllById(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void updatePopupSubmissionThrowsWhenUploadFileIndexIsOutOfRange() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    mockAdminAndPopupSubmission(popupSubmission);

    assertThatThrownBy(
            () ->
                popupAdminService.updatePopupSubmission(
                    ADMIN_UUID,
                    POPUP_SUBMISSION_ID,
                    approvalRequest(List.of(uploadImage(1, 0))),
                    List.of(imageFile("new-image.jpg", "image/jpeg"))))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(recommendRepository, never()).findAllById(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void updatePopupSubmissionPropagatesStorageValidationFailure() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    MockMultipartFile uploadImage = imageFile("new-image.gif", "image/gif");
    BaseException storageException = new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    mockAdminAndPopupSubmission(popupSubmission);
    mockRecommendList();
    when(popupSubmissionImageStorage.storeAll(any())).thenThrow(storageException);

    assertThatThrownBy(
            () ->
                popupAdminService.updatePopupSubmission(
                    ADMIN_UUID,
                    POPUP_SUBMISSION_ID,
                    approvalRequest(List.of(uploadImage(0, 0))),
                    List.of(uploadImage)))
        .isSameAs(storageException);

    verify(popupRepository, never()).saveAndFlush(any());
  }

  @Test
  void updatePopupSubmissionDeletesStoredUploadImagesWhenDbSaveFails() {
    PopupSubmission popupSubmission = pendingPopupSubmission();
    MockMultipartFile uploadImage = imageFile("new-image.jpg", "image/jpeg");
    List<String> storedImageUrls =
        List.of("/submissionImages/2026/07/11111111-1111-1111-1111-111111111111.jpg");
    RuntimeException dbException = new RuntimeException("db failed");
    mockAdminAndPopupSubmission(popupSubmission);
    mockRecommendList();
    when(popupSubmissionImageStorage.storeAll(any())).thenReturn(storedImageUrls);
    when(popupRepository.saveAndFlush(any(Popup.class))).thenThrow(dbException);

    assertThatThrownBy(
            () ->
                popupAdminService.updatePopupSubmission(
                    ADMIN_UUID,
                    POPUP_SUBMISSION_ID,
                    approvalRequest(List.of(uploadImage(0, 0))),
                    List.of(uploadImage)))
        .isSameAs(dbException);

    verify(popupSubmissionImageStorage).deleteAll(storedImageUrls);
  }

  private void mockAdminAndPopupSubmission(PopupSubmission popupSubmission) {
    Users admin = Users.builder().uuid(ADMIN_UUID).role(Role.ADMIN).build();
    when(usersRepository.findByUuid(ADMIN_UUID)).thenReturn(Optional.of(admin));
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(popupSubmission));
  }

  private void mockRecommendList() {
    Recommend recommend1 = mockRecommend(1L);
    Recommend recommend2 = mockRecommend(2L);
    when(recommendRepository.findAllById(any())).thenReturn(List.of(recommend1, recommend2));
  }

  private void mockPopupSave() {
    when(popupRepository.saveAndFlush(any(Popup.class)))
        .thenAnswer(
            invocation -> {
              Popup popup = invocation.getArgument(0);
              ReflectionTestUtils.setField(popup, "id", 1L);
              ReflectionTestUtils.setField(popup, "uuid", POPUP_UUID);
              return popup;
            });
  }

  private List<PopupImage> captureSavedPopupImages() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PopupImage>> imageCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(popupImageRepository).saveAllAndFlush(imageCaptor.capture());
    return StreamSupport.stream(imageCaptor.getValue().spliterator(), false).toList();
  }

  private PopupSubmission pendingPopupSubmission() {
    PopupSubmission popupSubmission =
        PopupSubmission.builder()
            .name("제보 팝업")
            .startDate(LocalDate.of(2026, 7, 1))
            .endDate(LocalDate.of(2026, 7, 31))
            .roadAddress("서울 성동구 왕십리로 123")
            .region("서울")
            .submitterUserUuid("submitter-uuid")
            .status(PopupSubmissionStatus.PENDING)
            .build();
    ReflectionTestUtils.setField(popupSubmission, "id", POPUP_SUBMISSION_ID);
    return popupSubmission;
  }

  private PopupSubmissionAdminUpdateRequestDto rejectedRequest() {
    PopupSubmissionAdminUpdateRequestDto request = new PopupSubmissionAdminUpdateRequestDto();
    ReflectionTestUtils.setField(request, "status", "REJECTED");
    return request;
  }

  private PopupSubmissionAdminUpdateRequestDto approvalRequest(
      List<PopupSubmissionAdminImageRequestDto> imageList) {
    PopupSubmissionAdminUpdateRequestDto request = new PopupSubmissionAdminUpdateRequestDto();
    ReflectionTestUtils.setField(request, "status", "APPROVED");
    ReflectionTestUtils.setField(request, "name", "성수 팝업스토어");
    ReflectionTestUtils.setField(request, "startDate", LocalDate.of(2026, 7, 1));
    ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 7, 31));
    ReflectionTestUtils.setField(request, "roadAddress", "서울 성동구 왕십리로 123");
    ReflectionTestUtils.setField(request, "region", "서울");
    ReflectionTestUtils.setField(request, "address", "서울 성동구 성수동1가 123");
    ReflectionTestUtils.setField(request, "openTime", LocalTime.of(10, 0));
    ReflectionTestUtils.setField(request, "closeTime", LocalTime.of(20, 0));
    ReflectionTestUtils.setField(request, "latitude", 37.123);
    ReflectionTestUtils.setField(request, "longitude", 127.123);
    ReflectionTestUtils.setField(request, "captionSummary", "성수 팝업 요약");
    ReflectionTestUtils.setField(request, "caption", "성수 팝업 상세 설명");
    ReflectionTestUtils.setField(request, "mediaType", "IMAGE");
    ReflectionTestUtils.setField(request, "recommendIdList", List.of(1L, 2L));
    ReflectionTestUtils.setField(request, "imageList", imageList);
    return request;
  }

  private PopupSubmissionAdminImageRequestDto existingImage(String imageUrl, Integer sortOrder) {
    PopupSubmissionAdminImageRequestDto image = new PopupSubmissionAdminImageRequestDto();
    ReflectionTestUtils.setField(image, "sourceType", "EXISTING");
    ReflectionTestUtils.setField(image, "imageUrl", imageUrl);
    ReflectionTestUtils.setField(image, "sortOrder", sortOrder);
    return image;
  }

  private PopupSubmissionAdminImageRequestDto uploadImage(Integer fileIndex, Integer sortOrder) {
    PopupSubmissionAdminImageRequestDto image = new PopupSubmissionAdminImageRequestDto();
    ReflectionTestUtils.setField(image, "sourceType", "UPLOAD");
    ReflectionTestUtils.setField(image, "fileIndex", fileIndex);
    ReflectionTestUtils.setField(image, "sortOrder", sortOrder);
    return image;
  }

  private MockMultipartFile imageFile(String originalFilename, String contentType) {
    return new MockMultipartFile(
        "images", originalFilename, contentType, "image".getBytes(StandardCharsets.UTF_8));
  }

  private Recommend mockRecommend(Long id) {
    Recommend recommend = org.mockito.Mockito.mock(Recommend.class);
    when(recommend.getId()).thenReturn(id);
    return recommend;
  }
}
