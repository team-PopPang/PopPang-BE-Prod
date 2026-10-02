package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 운영(prod)용 S3 저장소. URL /submissionImages/a/b.jpg는 key submissionImages/a/b.jpg에 저장한다. nginx가 같은
 * 경로의 key로 프록시하므로 DB에 저장되는 URL 형식은 파일시스템과 같다.
 */
@Slf4j
public class S3PopupSubmissionImageStore implements PopupSubmissionImageStore, AutoCloseable {

  private final S3Client s3Client;
  private final String bucket;
  private final String keyPrefix;

  public S3PopupSubmissionImageStore(S3Client s3Client, String bucket, String urlPrefix) {
    if (s3Client == null
        || !StringUtils.hasText(bucket)
        || urlPrefix == null
        || urlPrefix.length() < 2
        || !urlPrefix.startsWith("/")
        || urlPrefix.endsWith("/")) {
      throw new IllegalArgumentException("S3 제보 이미지 저장소 설정이 올바르지 않습니다.");
    }
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.keyPrefix = urlPrefix.substring(1);
  }

  @Override
  public void save(String relativePath, MultipartFile image, String contentType) {
    long contentLength = image.getSize();
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(toKey(relativePath))
            .contentType(contentType)
            .contentLength(contentLength)
            .build();

    try {
      // 재시도 때마다 새 스트림을 열 수 있게 content provider로 전달한다.
      s3Client.putObject(
          request,
          RequestBody.fromContentProvider(() -> openStream(image), contentLength, contentType));
    } catch (SdkException | UncheckedIOException e) {
      throw new BaseException(ErrorCode.INTERNAL_ERROR);
    }
  }

  @Override
  public void delete(String relativePath) {
    if (!isSafeRelativePath(relativePath)) {
      return;
    }

    String key = toKey(relativePath);
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (SdkException e) {
      log.warn("제보 이미지 S3 삭제 실패 key={} error={}", key, e.getClass().getSimpleName());
    }
  }

  @Override
  public void close() {
    s3Client.close();
  }

  private String toKey(String relativePath) {
    return keyPrefix + "/" + relativePath;
  }

  private static InputStream openStream(MultipartFile image) {
    try {
      return image.getInputStream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isSafeRelativePath(String relativePath) {
    if (!StringUtils.hasText(relativePath)
        || relativePath.startsWith("/")
        || relativePath.contains("\\")) {
      return false;
    }
    for (String segment : relativePath.split("/", -1)) {
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
        return false;
      }
    }
    return true;
  }
}
