package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

/** dev/local용 파일시스템 저장소. app.storage.submission-image-root 아래에 저장한다. */
public class FileSystemPopupSubmissionImageStore implements PopupSubmissionImageStore {

  private final String submissionImageRoot;

  public FileSystemPopupSubmissionImageStore(String submissionImageRoot) {
    this.submissionImageRoot = submissionImageRoot;
  }

  @Override
  public void save(String relativePath, MultipartFile image, String contentType) {
    Path filePath = getRootPath().resolve(relativePath);

    try {
      Files.createDirectories(filePath.getParent());
      try (InputStream inputStream = image.getInputStream()) {
        Files.copy(inputStream, filePath);
      }
    } catch (IOException e) {
      deleteFile(filePath);
      throw new BaseException(ErrorCode.INTERNAL_ERROR);
    }
  }

  @Override
  public void delete(String relativePath) {
    Path rootPath = getRootPath();
    Path filePath = rootPath.resolve(relativePath).normalize();
    if (!filePath.startsWith(rootPath)) {
      return;
    }

    deleteFile(filePath);
  }

  private void deleteFile(Path filePath) {
    try {
      Files.deleteIfExists(filePath);
    } catch (IOException ignored) {
    }
  }

  private Path getRootPath() {
    return Path.of(submissionImageRoot).toAbsolutePath().normalize();
  }
}
