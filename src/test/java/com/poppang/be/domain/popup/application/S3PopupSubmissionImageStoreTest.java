package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.application.PopupSubmissionImageStorageProperties.StorageType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3PopupSubmissionImageStoreTest {

  private static final String BUCKET = "test-submission-bucket";
  private static final String URL_PREFIX = "/submissionImages";
  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_DIRECTORY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy/MM");

  private final S3Client s3Client = mock(S3Client.class);

  @Test
  void storesImageAtObjectKeyEqualToUrlPathWithoutLeadingSlash() throws Exception {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    byte[] bytes = "jpeg-bytes".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile image = new MockMultipartFile("images", "popup.JPG", "image/jpeg", bytes);
    String dateDirectory = YearMonth.now(KOREA_ZONE_ID).format(DATE_DIRECTORY_FORMATTER);

    List<String> imageUrlPathList = createStorage().storeAll(List.of(image));

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3Client).putObject(request.capture(), body.capture());
    assertThat(imageUrlPathList).hasSize(1);
    String imageUrlPath = imageUrlPathList.get(0);
    assertThat(imageUrlPath)
        .matches("^/submissionImages/" + dateDirectory + "/[0-9a-f-]{36}\\.jpg$");
    assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(request.getValue().key()).isEqualTo(imageUrlPath.substring(1));
    assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
    assertThat(request.getValue().contentLength()).isEqualTo(bytes.length);
    assertThat(body.getValue().optionalContentLength()).contains((long) bytes.length);
    try (InputStream inputStream = body.getValue().contentStreamProvider().newStream()) {
      assertThat(inputStream.readAllBytes()).isEqualTo(bytes);
    }
  }

  @Test
  void setsContentTypeFromValidatedExtension() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    byte[] bytes = "image".getBytes(StandardCharsets.UTF_8);

    createStorage()
        .storeAll(
            List.of(
                new MockMultipartFile("images", "a.png", "image/jpeg", bytes),
                new MockMultipartFile("images", "b.jpeg", "image/jpeg", bytes),
                new MockMultipartFile("images", "c.heic", "image/heic", bytes),
                new MockMultipartFile("images", "d.HEIF", "image/heif", bytes)));

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client, times(4)).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getAllValues())
        .extracting(PutObjectRequest::contentType)
        .containsExactly("image/png", "image/jpeg", "image/heic", "image/heif");
    assertThat(request.getAllValues())
        .extracting(PutObjectRequest::key)
        .allMatch(key -> key.startsWith("submissionImages/"));
  }

  @Test
  void deletesOnlyObjectsUnderSubmissionImagePrefix() {
    createStorage()
        .deleteAll(
            Arrays.asList(
                "/submissionImages/2026/07/01fe3647-bbc7-4eaa-8dda-0a234ac269fd.jpg",
                "/images/2026/a.jpg",
                "/submissionImages/../images/a.jpg",
                "/submissionImages/2026//a.jpg",
                "/submissionImages/",
                null));

    ArgumentCaptor<DeleteObjectRequest> request =
        ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(request.capture());
    verifyNoMoreInteractions(s3Client);
    assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(request.getValue().key())
        .isEqualTo("submissionImages/2026/07/01fe3647-bbc7-4eaa-8dda-0a234ac269fd.jpg");
  }

  @Test
  void deletesAlreadyStoredObjectsWhenLaterUploadFails() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build())
        .thenThrow(SdkClientException.create("upload failed"));
    byte[] bytes = "image".getBytes(StandardCharsets.UTF_8);
    List<MultipartFile> images =
        List.of(
            new MockMultipartFile("images", "a.jpg", "image/jpeg", bytes),
            new MockMultipartFile("images", "b.jpg", "image/jpeg", bytes));

    assertThatThrownBy(() -> createStorage().storeAll(images))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INTERNAL_ERROR);

    ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client, times(2)).putObject(put.capture(), any(RequestBody.class));
    verify(s3Client).deleteObject(delete.capture());
    assertThat(delete.getValue().key()).isEqualTo(put.getAllValues().get(0).key());
  }

  @Test
  void ignoresDeleteFailureLikeFileSystemStore() {
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(SdkClientException.create("delete failed"))
        .thenReturn(DeleteObjectResponse.builder().build());

    assertThatCode(
            () ->
                createStorage()
                    .deleteAll(
                        List.of(
                            "/submissionImages/2026/07/a.jpg", "/submissionImages/2026/07/b.jpg")))
        .doesNotThrowAnyException();
    verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
  }

  private PopupSubmissionImageStorage createStorage() {
    PopupSubmissionImageStorageProperties properties =
        new PopupSubmissionImageStorageProperties(
            null, URL_PREFIX, StorageType.S3, BUCKET, "ap-northeast-2");
    return new PopupSubmissionImageStorage(
        properties,
        new S3PopupSubmissionImageStore(
            s3Client, BUCKET, properties.normalizedSubmissionImageUrlPrefix()));
  }
}
