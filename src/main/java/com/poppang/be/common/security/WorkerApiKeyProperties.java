package com.poppang.be.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.worker")
public record WorkerApiKeyProperties(String apiKey) {

  private static final int MINIMUM_API_KEY_LENGTH = 32;

  public WorkerApiKeyProperties {
    if (apiKey == null || apiKey.isBlank() || apiKey.length() < MINIMUM_API_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "internal.worker.api-key must contain at least 32 characters");
    }
  }

  @Override
  public String toString() {
    return "WorkerApiKeyProperties[apiKey=[REDACTED]]";
  }
}
