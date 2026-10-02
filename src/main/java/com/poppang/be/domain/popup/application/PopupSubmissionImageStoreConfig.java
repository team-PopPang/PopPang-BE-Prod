package com.poppang.be.domain.popup.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class PopupSubmissionImageStoreConfig {

  /**
   * app.storage.submission-image-storage-type으로 저장소를 고른다(기본 파일시스템). S3 자격증명은 코드에 두지 않고 기본 자격증명
   * 체인(운영 EC2 인스턴스 역할)을 쓴다.
   */
  @Bean
  public PopupSubmissionImageStore popupSubmissionImageStore(
      PopupSubmissionImageStorageProperties properties) {
    return switch (properties.submissionImageStorageType()) {
      case FILESYSTEM -> new FileSystemPopupSubmissionImageStore(properties.submissionImageRoot());
      case S3 -> new S3PopupSubmissionImageStore(
          S3Client.builder().region(Region.of(properties.submissionImageS3Region())).build(),
          properties.submissionImageS3Bucket(),
          properties.normalizedSubmissionImageUrlPrefix());
    };
  }
}
