package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class PopupSubmissionImageStorage {

  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_DIRECTORY_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy/MM");
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/heic", "image/heif");
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of("jpg", "jpeg", "png", "heic", "heif");

  private final PopupSubmissionImageStorageProperties properties;

  public List<String> storeAll(List<MultipartFile> images) {
    List<String> storedImageUrlPathList = new ArrayList<>();
    try {
      for (MultipartFile image : images) {
        storedImageUrlPathList.add(store(image));
      }
      return storedImageUrlPathList;
    } catch (RuntimeException e) {
      deleteAll(storedImageUrlPathList);
      throw e;
    }
  }

  public void deleteAll(List<String> imageUrlPathList) {
    if (imageUrlPathList == null) {
      return;
    }

    for (String imageUrlPath : imageUrlPathList) {
      delete(imageUrlPath);
    }
  }

  private String store(MultipartFile image) {
    validateImage(image);

    String extension = getExtension(image);
    String dateDirectory = YearMonth.now(KOREA_ZONE_ID).format(DATE_DIRECTORY_FORMATTER);
    String filename = UUID.randomUUID() + "." + extension;
    Path directoryPath = getRootPath().resolve(dateDirectory);
    Path filePath = directoryPath.resolve(filename);

    try {
      Files.createDirectories(directoryPath);
      try (InputStream inputStream = image.getInputStream()) {
        Files.copy(inputStream, filePath);
      }
    } catch (IOException e) {
      deleteFile(filePath);
      throw new BaseException(ErrorCode.INTERNAL_ERROR);
    }

    return normalizeUrlPrefix() + "/" + dateDirectory + "/" + filename;
  }

  private void validateImage(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }

    String contentType = image.getContentType();
    if (contentType == null
        || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }

    String extension = getExtension(image);
    if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }
  }

  private String getExtension(MultipartFile image) {
    String extension = StringUtils.getFilenameExtension(image.getOriginalFilename());
    if (extension == null) {
      return null;
    }

    return extension.toLowerCase(Locale.ROOT);
  }

  private void delete(String imageUrlPath) {
    Path filePath = resolveFilePath(imageUrlPath);
    if (filePath == null) {
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

  private Path resolveFilePath(String imageUrlPath) {
    if (imageUrlPath == null || !imageUrlPath.startsWith(normalizeUrlPrefix() + "/")) {
      return null;
    }

    String relativePath = imageUrlPath.substring((normalizeUrlPrefix() + "/").length());
    Path rootPath = getRootPath();
    Path filePath = rootPath.resolve(relativePath).normalize();
    if (!filePath.startsWith(rootPath)) {
      return null;
    }

    return filePath;
  }

  private Path getRootPath() {
    return Path.of(properties.submissionImageRoot()).toAbsolutePath().normalize();
  }

  private String normalizeUrlPrefix() {
    String urlPrefix = properties.submissionImageUrlPrefix();
    if (!urlPrefix.startsWith("/")) {
      urlPrefix = "/" + urlPrefix;
    }
    if (urlPrefix.endsWith("/")) {
      return urlPrefix.substring(0, urlPrefix.length() - 1);
    }
    return urlPrefix;
  }
}
