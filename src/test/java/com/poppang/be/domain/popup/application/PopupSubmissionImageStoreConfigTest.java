package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class PopupSubmissionImageStoreConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(StorageTestConfiguration.class)
          .withPropertyValues(
              "app.storage.submission-image-root=/tmp/poppang-submission-test",
              "app.storage.submission-image-url-prefix=/submissionImages");

  @Test
  void usesFileSystemStoreWhenStorageTypeIsNotConfigured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PopupSubmissionImageStore.class);
          assertThat(context.getBean(PopupSubmissionImageStore.class))
              .isInstanceOf(FileSystemPopupSubmissionImageStore.class);
        });
  }

  @Test
  void usesFileSystemStoreWhenConfiguredExplicitly() {
    contextRunner
        .withPropertyValues("app.storage.submission-image-storage-type=filesystem")
        .run(
            context ->
                assertThat(context.getBean(PopupSubmissionImageStore.class))
                    .isInstanceOf(FileSystemPopupSubmissionImageStore.class));
  }

  @Test
  void usesS3StoreWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "app.storage.submission-image-storage-type=s3",
            "app.storage.submission-image-s3-bucket=test-submission-bucket",
            "app.storage.submission-image-s3-region=ap-northeast-2")
        .run(
            context -> {
              assertThat(context).hasSingleBean(PopupSubmissionImageStore.class);
              assertThat(context.getBean(PopupSubmissionImageStore.class))
                  .isInstanceOf(S3PopupSubmissionImageStore.class);
            });
  }

  @Test
  void failsToStartWhenS3BucketOrRegionIsMissing() {
    contextRunner
        .withPropertyValues(
            "app.storage.submission-image-storage-type=s3",
            "app.storage.submission-image-s3-region=ap-northeast-2")
        .run(context -> assertThat(context).hasFailed());
    contextRunner
        .withPropertyValues(
            "app.storage.submission-image-storage-type=s3",
            "app.storage.submission-image-s3-bucket=test-submission-bucket")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsToStartWithUnknownStorageType() {
    contextRunner
        .withPropertyValues("app.storage.submission-image-storage-type=ftp")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(PopupSubmissionImageStorageProperties.class)
  @Import({PopupSubmissionImageStoreConfig.class, PopupSubmissionImageStorage.class})
  static class StorageTestConfiguration {}
}
