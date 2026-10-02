package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  // 저장 시 Content-Type은 검증한 확장자 기준으로 정한다.
  private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION =
      Map.of(
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg",
          "png", "image/png",
          "heic", "image/heic",
          "heif", "image/heif");

  private final PopupSubmissionImageStorageProperties properties;
  private final PopupSubmissionImageStore imageStore;

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
    String relativePath = dateDirectory + "/" + filename;

    imageStore.save(relativePath, image, CONTENT_TYPE_BY_EXTENSION.get(extension));

    return properties.normalizedSubmissionImageUrlPrefix() + "/" + relativePath;
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
    String relativePath = resolveRelativePath(imageUrlPath);
    if (relativePath == null) {
      return;
    }

    imageStore.delete(relativePath);
  }

  private String resolveRelativePath(String imageUrlPath) {
    String urlPathPrefix = properties.normalizedSubmissionImageUrlPrefix() + "/";
    if (imageUrlPath == null || !imageUrlPath.startsWith(urlPathPrefix)) {
      return null;
    }

    String relativePath = imageUrlPath.substring(urlPathPrefix.length());
    return relativePath.isEmpty() ? null : relativePath;
  }
}
