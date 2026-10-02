package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.poppang.be.domain.popup.application.PopupSubmissionImageStorageProperties.StorageType;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 루프백 스텁 서버로 S3 PUT·DELETE가 실제 SDK HTTP 클라이언트를 거쳐 나가는지 확인한다(외부 네트워크 없음). */
class S3PopupSubmissionImageStoreHttpTest {

  private static final String BUCKET = "test-submission-bucket";

  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private HttpServer server;

  @BeforeEach
  void startStubServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = exchange.getRequestBody().readAllBytes();
          requests.add(
              new RecordedRequest(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getRawPath(),
                  exchange.getRequestHeaders().getFirst("Content-Type"),
                  new String(body, StandardCharsets.ISO_8859_1)));
          exchange.getResponseHeaders().add("ETag", "\"stub-etag\"");
          exchange.sendResponseHeaders(
              "DELETE".equals(exchange.getRequestMethod()) ? 204 : 200, -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopStubServer() {
    server.stop(0);
  }

  @Test
  void storesAndDeletesThroughSdkHttpClient() {
    String imageUrlPath;
    try (S3Client s3Client =
        S3Client.builder()
            .endpointOverride(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .forcePathStyle(true)
            .build()) {
      PopupSubmissionImageStorageProperties properties =
          new PopupSubmissionImageStorageProperties(
              null, "/submissionImages", StorageType.S3, BUCKET, "ap-northeast-2");
      PopupSubmissionImageStorage storage =
          new PopupSubmissionImageStorage(
              properties,
              new S3PopupSubmissionImageStore(
                  s3Client, BUCKET, properties.normalizedSubmissionImageUrlPrefix()));

      imageUrlPath =
          storage
              .storeAll(
                  List.of(
                      new MockMultipartFile(
                          "images",
                          "popup.png",
                          "image/png",
                          "loopback-image-bytes".getBytes(StandardCharsets.UTF_8))))
              .get(0);
      storage.deleteAll(List.of(imageUrlPath));
    }

    assertThat(requests).hasSize(2);
    RecordedRequest put = requests.get(0);
    assertThat(put.method()).isEqualTo("PUT");
    assertThat(put.path()).isEqualTo("/" + BUCKET + imageUrlPath);
    assertThat(put.contentType()).isEqualTo("image/png");
    assertThat(put.body()).contains("loopback-image-bytes");
    RecordedRequest delete = requests.get(1);
    assertThat(delete.method()).isEqualTo("DELETE");
    assertThat(delete.path()).isEqualTo(put.path());
  }

  private record RecordedRequest(String method, String path, String contentType, String body) {}
}
