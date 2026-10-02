package com.poppang.be.domain.popup.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.storage")
public record PopupSubmissionImageStorageProperties(
    String submissionImageRoot,
    String submissionImageUrlPrefix,
    StorageType submissionImageStorageType,
    String submissionImageS3Bucket,
    String submissionImageS3Region) {

  /** 제보 이미지 저장 방식. 운영(prod)은 S3, dev/local은 파일시스템을 쓴다. */
  public enum StorageType {
    FILESYSTEM,
    S3
  }

  public PopupSubmissionImageStorageProperties(
      String submissionImageRoot, String submissionImageUrlPrefix) {
    this(submissionImageRoot, submissionImageUrlPrefix, StorageType.FILESYSTEM, null, null);
  }

  @ConstructorBinding
  public PopupSubmissionImageStorageProperties {
    if (submissionImageStorageType == null) {
      submissionImageStorageType = StorageType.FILESYSTEM;
    }
    if (submissionImageStorageType == StorageType.S3
        && (!StringUtils.hasText(submissionImageUrlPrefix)
            || !StringUtils.hasText(submissionImageS3Bucket)
            || !StringUtils.hasText(submissionImageS3Region))) {
      throw new IllegalArgumentException(
          "S3 제보 이미지 저장에는 app.storage.submission-image-url-prefix, "
              + "submission-image-s3-bucket, submission-image-s3-region이 필요합니다.");
    }
  }

  /** 앞에 '/'가 있고 끝에 '/'가 없는 URL prefix. 예: /submissionImages */
  public String normalizedSubmissionImageUrlPrefix() {
    String urlPrefix = submissionImageUrlPrefix;
    if (!urlPrefix.startsWith("/")) {
      urlPrefix = "/" + urlPrefix;
    }
    if (urlPrefix.endsWith("/")) {
      return urlPrefix.substring(0, urlPrefix.length() - 1);
    }
    return urlPrefix;
  }
}
