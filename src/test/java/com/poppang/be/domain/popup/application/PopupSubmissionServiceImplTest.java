package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionImage;
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
class PopupSubmissionServiceImplTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

  @Mock private UsersRepository usersRepository;

  @Mock private PopupSubmissionRepository popupSubmissionRepository;

  @Mock private PopupSubmissionImageRepository popupSubmissionImageRepository;

  @Mock private PopupSubmissionRecommendRepository popupSubmissionRecommendRepository;

  @Mock private RecommendRepository recommendRepository;

  @Mock private PopupSubmissionImageStorage popupSubmissionImageStorage;

  @InjectMocks private PopupSubmissionServiceImpl popupSubmissionService;

  @Test
  void createPopupSubmissionSavesStoredMultipartImageUrlsInFileOrder() {
    PopupSubmissionCreateRequestDto request = createRequest();
    List<MultipartFile> images = createImages();
    List<String> imageUrlPathList =
        List.of(
            "/submissionImages/2026/07/11111111-1111-1111-1111-111111111111.jpg",
            "/submissionImages/2026/07/22222222-2222-2222-2222-222222222222.png");
    Users user = Users.builder().uuid(USER_UUID).build();
    Recommend recommend1 = mockRecommend(1L);
    Recommend recommend2 = mockRecommend(2L);

    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID)).thenReturn(Optional.of(user));
    when(recommendRepository.findAllById(any())).thenReturn(List.of(recommend1, recommend2));
    when(popupSubmissionImageStorage.storeAll(images)).thenReturn(imageUrlPathList);
    when(popupSubmissionRepository.saveAndFlush(any(PopupSubmission.class)))
        .thenAnswer(
            invocation -> {
              PopupSubmission popupSubmission = invocation.getArgument(0);
              ReflectionTestUtils.setField(popupSubmission, "id", 1L);
              return popupSubmission;
            });

    popupSubmissionService.createPopupSubmission(request, images);

    verify(popupSubmissionImageStorage).storeAll(images);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PopupSubmissionImage>> imageCaptor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(popupSubmissionImageRepository).saveAllAndFlush(imageCaptor.capture());

    List<PopupSubmissionImage> savedImages =
        StreamSupport.stream(imageCaptor.getValue().spliterator(), false).toList();
    assertThat(savedImages).hasSize(2);
    assertThat(savedImages.get(0).getImageUrl()).isEqualTo(imageUrlPathList.get(0));
    assertThat(savedImages.get(0).getSortOrder()).isZero();
    assertThat(savedImages.get(1).getImageUrl()).isEqualTo(imageUrlPathList.get(1));
    assertThat(savedImages.get(1).getSortOrder()).isEqualTo(1);
  }

  @Test
  void createPopupSubmissionThrowsWhenImagesIsNull() {
    assertThatThrownBy(() -> popupSubmissionService.createPopupSubmission(createRequest(), null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenImagesIsEmpty() {
    assertThatThrownBy(
            () -> popupSubmissionService.createPopupSubmission(createRequest(), List.of()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenMultipartFileIsEmpty() {
    MockMultipartFile emptyImage =
        new MockMultipartFile("images", "empty.jpg", "image/jpeg", new byte[0]);

    assertThatThrownBy(
            () ->
                popupSubmissionService.createPopupSubmission(createRequest(), List.of(emptyImage)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenRequestIsNull() {
    assertThatThrownBy(() -> popupSubmissionService.createPopupSubmission(null, createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenRequiredFieldIsBlank() {
    PopupSubmissionCreateRequestDto request = createRequest();
    ReflectionTestUtils.setField(request, "name", " ");

    assertThatThrownBy(() -> popupSubmissionService.createPopupSubmission(request, createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);

    verify(usersRepository, never()).findByUuidAndDeletedFalse(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenUserUuidDoesNotExist() {
    PopupSubmissionCreateRequestDto request = createRequest();

    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> popupSubmissionService.createPopupSubmission(request, createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    verify(recommendRepository, never()).findAllById(any());
    verify(popupSubmissionImageStorage, never()).storeAll(any());
  }

  @Test
  void createPopupSubmissionThrowsWhenRecommendIdDoesNotExist() {
    PopupSubmissionCreateRequestDto request = createRequest();
    Users user = Users.builder().uuid(USER_UUID).build();
    Recommend recommend = mock(Recommend.class);

    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID)).thenReturn(Optional.of(user));
    when(recommendRepository.findAllById(any())).thenReturn(List.of(recommend));

    assertThatThrownBy(() -> popupSubmissionService.createPopupSubmission(request, createImages()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_RECOMMEND_ID);

    verify(popupSubmissionImageStorage, never()).storeAll(any());
    verify(popupSubmissionRepository, never()).saveAndFlush(any());
  }

  @Test
  void createPopupSubmissionDeletesStoredImagesWhenDbSaveFails() {
    PopupSubmissionCreateRequestDto request = createRequest();
    List<MultipartFile> images = createImages();
    List<String> imageUrlPathList =
        List.of("/submissionImages/2026/07/11111111-1111-1111-1111-111111111111.jpg");
    RuntimeException dbException = new RuntimeException("db failed");
    Users user = Users.builder().uuid(USER_UUID).build();
    Recommend recommend1 = mockRecommend(1L);
    Recommend recommend2 = mockRecommend(2L);

    when(usersRepository.findByUuidAndDeletedFalse(USER_UUID)).thenReturn(Optional.of(user));
    when(recommendRepository.findAllById(any())).thenReturn(List.of(recommend1, recommend2));
    when(popupSubmissionImageStorage.storeAll(images)).thenReturn(imageUrlPathList);
    when(popupSubmissionRepository.saveAndFlush(any(PopupSubmission.class))).thenThrow(dbException);

    assertThatThrownBy(() -> popupSubmissionService.createPopupSubmission(request, images))
        .isSameAs(dbException);

    verify(popupSubmissionImageStorage).deleteAll(imageUrlPathList);
  }

  private PopupSubmissionCreateRequestDto createRequest() {
    PopupSubmissionCreateRequestDto request = new PopupSubmissionCreateRequestDto();
    ReflectionTestUtils.setField(request, "userUuid", USER_UUID);
    ReflectionTestUtils.setField(request, "name", "테스트 팝업");
    ReflectionTestUtils.setField(request, "startDate", LocalDate.of(2026, 7, 1));
    ReflectionTestUtils.setField(request, "endDate", LocalDate.of(2026, 7, 31));
    ReflectionTestUtils.setField(request, "roadAddress", "서울 성동구 왕십리로 123");
    ReflectionTestUtils.setField(request, "region", "서울");
    ReflectionTestUtils.setField(request, "description", "성수 팝업 설명");
    ReflectionTestUtils.setField(request, "recommendIdList", List.of(1L, 2L));
    return request;
  }

  private List<MultipartFile> createImages() {
    return List.of(
        new MockMultipartFile(
            "images", "image1.jpg", "image/jpeg", "image1".getBytes(StandardCharsets.UTF_8)),
        new MockMultipartFile(
            "images", "image2.png", "image/png", "image2".getBytes(StandardCharsets.UTF_8)));
  }

  private Recommend mockRecommend(Long id) {
    Recommend recommend = mock(Recommend.class);
    when(recommend.getId()).thenReturn(id);
    return recommend;
  }
}
