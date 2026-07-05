package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class PopupSubmissionImageStorageTest {

  private static final String URL_PREFIX = "/submissionImages";
  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_DIRECTORY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy/MM");

  @TempDir Path tempDir;

  @Test
  void storeAllReturnsUrlPathAndSavesFile() {
    PopupSubmissionImageStorage storage = createStorage();
    String dateDirectory = YearMonth.now(KOREA_ZONE_ID).format(DATE_DIRECTORY_FORMATTER);
    MockMultipartFile image =
        new MockMultipartFile(
            "images", "popup.JPG", "image/jpeg", "image".getBytes(StandardCharsets.UTF_8));

    List<String> imageUrlPathList = storage.storeAll(List.of(image));

    assertThat(imageUrlPathList).hasSize(1);
    String imageUrlPath = imageUrlPathList.get(0);
    assertThat(imageUrlPath)
        .matches("^/submissionImages/" + dateDirectory + "/[0-9a-f-]{36}\\.jpg$");

    String relativePath = imageUrlPath.substring((URL_PREFIX + "/").length());
    assertThat(Files.exists(tempDir.resolve(relativePath))).isTrue();
  }

  @Test
  void storeAllRejectsEmptyFile() {
    PopupSubmissionImageStorage storage = createStorage();
    MockMultipartFile image =
        new MockMultipartFile("images", "empty.jpg", "image/jpeg", new byte[0]);

    assertThatThrownBy(() -> storage.storeAll(List.of(image)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
  }

  @Test
  void storeAllRejectsInvalidContentType() {
    PopupSubmissionImageStorage storage = createStorage();
    MockMultipartFile image =
        new MockMultipartFile(
            "images", "popup.jpg", "text/plain", "image".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> storage.storeAll(List.of(image)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
  }

  @Test
  void storeAllRejectsInvalidExtension() {
    PopupSubmissionImageStorage storage = createStorage();
    MockMultipartFile image =
        new MockMultipartFile(
            "images", "popup.gif", "image/jpeg", "image".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> storage.storeAll(List.of(image)))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
  }

  private PopupSubmissionImageStorage createStorage() {
    return new PopupSubmissionImageStorage(
        new PopupSubmissionImageStorageProperties(tempDir.toString(), URL_PREFIX));
  }
}
