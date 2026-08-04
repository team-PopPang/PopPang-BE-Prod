package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminImageRequestDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminListResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionImage;
import com.poppang.be.domain.popup.entity.PopupSubmissionRecommend;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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

@ExtendWith(MockitoExtension.class)
class V2PopupAdminServiceImplTest {

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

  @InjectMocks private V2PopupAdminServiceImpl popupAdminService;

  @Test
  void deactivatePopupUsesOnlyTargetUuidAndDeactivatesPopup() {
    Popup popup = Popup.builder().uuid(POPUP_UUID).activated(true).build();
    when(popupRepository.findByUuid(POPUP_UUID)).thenReturn(Optional.of(popup));

    popupAdminService.deactivatePopup(POPUP_UUID);

    assertThat(ReflectionTestUtils.getField(popup, "activated")).isEqualTo(false);
    verifyNoInteractions(usersRepository);
  }

  @Test
  void deactivatePopupKeepsLegacyNotFoundError() {
    when(popupRepository.findByUuid(POPUP_UUID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> popupAdminService.deactivatePopup(POPUP_UUID))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.POPUP_NOT_FOUND);
  }

  @Test
  void getPopupSubmissionsUsesKoreaTodayAndMapsSubmitterNickname() {
    PopupSubmission submission = pendingPopupSubmission();
    Users submitter = Users.builder().uuid("submitter-uuid").nickname("제보자").build();
    when(popupSubmissionRepository.findByEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(any()))
        .thenReturn(List.of(submission));
    when(usersRepository.findByUuidIn(List.of("submitter-uuid"))).thenReturn(List.of(submitter));

    List<V2PopupSubmissionAdminListResponseDto> responses =
        popupAdminService.getPopupSubmissions("전체");

    ArgumentCaptor<LocalDate> todayCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(popupSubmissionRepository)
        .findByEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(todayCaptor.capture());
    assertThat(todayCaptor.getValue()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).getPopupSubmissionId()).isEqualTo(POPUP_SUBMISSION_ID);
    assertThat(responses.get(0).getSubmitterNickname()).isEqualTo("제보자");
  }

  @Test
  void getPopupSubmissionsUsesStatusFilterAndRejectsInvalidValue() {
    when(popupSubmissionRepository.findByStatusAndEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(
            any(), any()))
        .thenReturn(List.of());

    assertThat(popupAdminService.getPopupSubmissions("대기")).isEmpty();

    verify(popupSubmissionRepository)
        .findByStatusAndEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(
            PopupSubmissionStatus.PENDING, LocalDate.now(ZoneId.of("Asia/Seoul")));
    assertThatThrownBy(() -> popupAdminService.getPopupSubmissions("잘못된 상태"))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_STATUS);
  }

  @Test
  void getPopupSubmissionDetailUsesExistingImageAndRecommendQueries() {
    PopupSubmission submission = pendingPopupSubmission();
    PopupSubmissionImage image =
        PopupSubmissionImage.builder()
            .popupSubmission(submission)
            .imageUrl("/submissionImages/existing.jpg")
            .sortOrder(0)
            .build();
    Recommend recommend = mockRecommend(3L);
    PopupSubmissionRecommend submissionRecommend =
        PopupSubmissionRecommend.builder().popupSubmission(submission).recommend(recommend).build();
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(submission));
    when(popupSubmissionImageRepository.findAllByPopupSubmission_IdOrderBySortOrderAscIdAsc(
            POPUP_SUBMISSION_ID))
        .thenReturn(List.of(image));
    when(popupSubmissionRecommendRepository.findAllByPopupSubmissionIdWithRecommend(
            POPUP_SUBMISSION_ID))
        .thenReturn(List.of(submissionRecommend));

    V2PopupSubmissionAdminDetailResponseDto response =
        popupAdminService.getPopupSubmissionDetail(POPUP_SUBMISSION_ID);

    assertThat(response.getPopupSubmissionId()).isEqualTo(POPUP_SUBMISSION_ID);
    assertThat(response.getImageList()).extracting("imageUrl").containsExactly(image.getImageUrl());
    assertThat(response.getRecommendIdList()).containsExactly(3L);
  }

  @Test
  void getPopupSubmissionDetailKeepsLegacyNotFoundError() {
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> popupAdminService.getPopupSubmissionDetail(POPUP_SUBMISSION_ID))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.POPUP_SUBMISSION_NOT_FOUND);
  }

  @Test
  void updatePopupSubmissionRejectsWithoutImages() {
    PopupSubmission submission = pendingPopupSubmission();
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(submission));

    V2PopupSubmissionAdminUpdateResponseDto response =
        popupAdminService.updatePopupSubmission(POPUP_SUBMISSION_ID, rejectedRequest(), null);

    assertThat(response.getPopupUuid()).isNull();
    assertThat(submission.getStatus()).isEqualTo(PopupSubmissionStatus.REJECTED);
    verifyNoInteractions(
        popupRepository, popupImageRepository, popupRecommendRepository, recommendRepository);
    verifyNoInteractions(popupSubmissionImageStorage);
  }

  @Test
  void updatePopupSubmissionApprovesExistingAndUploadImagesInSortOrder() {
    PopupSubmission submission = pendingPopupSubmission();
    MockMultipartFile upload = imageFile("new.jpg", "image/jpeg");
    String uploadedUrl = "/submissionImages/new.jpg";
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(submission));
    mockRecommendList();
    mockPopupSave();
    when(popupSubmissionImageStorage.storeAll(any())).thenReturn(List.of(uploadedUrl));

    V2PopupSubmissionAdminUpdateResponseDto response =
        popupAdminService.updatePopupSubmission(
            POPUP_SUBMISSION_ID,
            approvalRequest(
                List.of(uploadImage(0, 1), existingImage("/submissionImages/existing.jpg", 0))),
            List.of(upload));

    assertThat(response.getPopupUuid()).isEqualTo(POPUP_UUID);
    assertThat(submission.getStatus()).isEqualTo(PopupSubmissionStatus.APPROVED);
    List<PopupImage> savedImages = captureSavedPopupImages();
    assertThat(savedImages)
        .extracting(PopupImage::getImageUrl)
        .containsExactly("/submissionImages/existing.jpg", uploadedUrl);
    assertThat(savedImages).extracting(PopupImage::getSortOrder).containsExactly(0, 1);
    verify(popupSubmissionRepository).flush();
  }

  @Test
  void updatePopupSubmissionDeletesUploadedImagesWhenDatabaseSaveFails() {
    PopupSubmission submission = pendingPopupSubmission();
    MockMultipartFile upload = imageFile("new.jpg", "image/jpeg");
    List<String> storedUrls = List.of("/submissionImages/new.jpg");
    RuntimeException databaseFailure = new RuntimeException("db failed");
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(submission));
    mockRecommendList();
    when(popupSubmissionImageStorage.storeAll(any())).thenReturn(storedUrls);
    when(popupRepository.saveAndFlush(any(Popup.class))).thenThrow(databaseFailure);

    assertThatThrownBy(
            () ->
                popupAdminService.updatePopupSubmission(
                    POPUP_SUBMISSION_ID,
                    approvalRequest(List.of(uploadImage(0, 0))),
                    List.of(upload)))
        .isSameAs(databaseFailure);

    verify(popupSubmissionImageStorage).deleteAll(storedUrls);
  }

  @Test
  void updatePopupSubmissionRejectsAlreadyProcessedSubmission() {
    PopupSubmission submission = pendingPopupSubmission();
    submission.updateStatus(PopupSubmissionStatus.APPROVED);
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(submission));

    assertThatThrownBy(
            () ->
                popupAdminService.updatePopupSubmission(
                    POPUP_SUBMISSION_ID, rejectedRequest(), null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.POPUP_SUBMISSION_ALREADY_PROCESSED);
  }

  @Test
  void updateSubmissionStatusKeepsLegacyStateChangeBehavior() {
    PopupSubmission submission = pendingPopupSubmission();
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(submission));

    popupAdminService.updateSubmissionStatus(
        POPUP_SUBMISSION_ID, statusRequest(PopupSubmissionStatus.REJECTED));

    assertThat(submission.getStatus()).isEqualTo(PopupSubmissionStatus.REJECTED);
  }

  @Test
  void updateSubmissionStatusKeepsLegacyErrorCodes() {
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                popupAdminService.updateSubmissionStatus(
                    POPUP_SUBMISSION_ID, statusRequest(PopupSubmissionStatus.REJECTED)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.POPUP_NOT_FOUND);

    PopupSubmission processed = pendingPopupSubmission();
    processed.updateStatus(PopupSubmissionStatus.APPROVED);
    when(popupSubmissionRepository.findById(POPUP_SUBMISSION_ID))
        .thenReturn(Optional.of(processed));

    assertThatThrownBy(
            () ->
                popupAdminService.updateSubmissionStatus(
                    POPUP_SUBMISSION_ID, statusRequest(PopupSubmissionStatus.REJECTED)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FAVORITE_ALREADY_EXISTS);
  }

  private PopupSubmission pendingPopupSubmission() {
    PopupSubmission submission =
        PopupSubmission.builder()
            .name("제보 팝업")
            .startDate(LocalDate.of(2026, 8, 1))
            .endDate(LocalDate.of(2026, 8, 31))
            .roadAddress("서울 성동구 왕십리로 123")
            .region("서울")
            .submitterUserUuid("submitter-uuid")
            .status(PopupSubmissionStatus.PENDING)
            .build();
    ReflectionTestUtils.setField(submission, "id", POPUP_SUBMISSION_ID);
    ReflectionTestUtils.setField(submission, "createdAt", LocalDateTime.of(2026, 8, 1, 10, 0));
    return submission;
  }

  private V2PopupSubmissionAdminUpdateRequestDto rejectedRequest() {
    V2PopupSubmissionAdminUpdateRequestDto request = new V2PopupSubmissionAdminUpdateRequestDto();
    ReflectionTestUtils.setField(request, "status", "REJECTED");
    return request;
  }

  private V2PopupSubmissionAdminUpdateRequestDto approvalRequest(
      List<V2PopupSubmissionAdminImageRequestDto> imageList) {
    V2PopupSubmissionAdminUpdateRequestDto request = new V2PopupSubmissionAdminUpdateRequestDto();
    ReflectionTestUtils.setField(request, "status", "APPROVED");
    ReflectionTestUtils.setField(request, "name", "성수 팝업스토어");
    ReflectionTestUtils.setField(request, "startDate", LocalDate.of(2026, 8, 1));
    ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 8, 31));
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

  private V2PopupSubmissionAdminImageRequestDto existingImage(String imageUrl, Integer sortOrder) {
    V2PopupSubmissionAdminImageRequestDto image = new V2PopupSubmissionAdminImageRequestDto();
    ReflectionTestUtils.setField(image, "sourceType", "EXISTING");
    ReflectionTestUtils.setField(image, "imageUrl", imageUrl);
    ReflectionTestUtils.setField(image, "sortOrder", sortOrder);
    return image;
  }

  private V2PopupSubmissionAdminImageRequestDto uploadImage(Integer fileIndex, Integer sortOrder) {
    V2PopupSubmissionAdminImageRequestDto image = new V2PopupSubmissionAdminImageRequestDto();
    ReflectionTestUtils.setField(image, "sourceType", "UPLOAD");
    ReflectionTestUtils.setField(image, "fileIndex", fileIndex);
    ReflectionTestUtils.setField(image, "sortOrder", sortOrder);
    return image;
  }

  private V2PopupSubmissionStatusUpdateRequestDto statusRequest(PopupSubmissionStatus status) {
    V2PopupSubmissionStatusUpdateRequestDto request = new V2PopupSubmissionStatusUpdateRequestDto();
    ReflectionTestUtils.setField(request, "popupSubmissionStatus", status);
    return request;
  }

  private void mockRecommendList() {
    Recommend character = mockRecommend(1L);
    Recommend fashion = mockRecommend(2L);
    when(recommendRepository.findAllById(any())).thenReturn(List.of(character, fashion));
  }

  private Recommend mockRecommend(Long id) {
    Recommend recommend = org.mockito.Mockito.mock(Recommend.class);
    when(recommend.getId()).thenReturn(id);
    return recommend;
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
    ArgumentCaptor<Iterable<PopupImage>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(popupImageRepository).saveAllAndFlush(captor.capture());
    return StreamSupport.stream(captor.getValue().spliterator(), false).toList();
  }

  private MockMultipartFile imageFile(String filename, String contentType) {
    return new MockMultipartFile(
        "images", filename, contentType, "image".getBytes(StandardCharsets.UTF_8));
  }
}
