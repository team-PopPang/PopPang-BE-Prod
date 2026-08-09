package com.poppang.be.domain.auth.config;

import com.poppang.be.domain.users.entity.Role;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qa.auth")
public record QaTokenProperties(String apiKey, String memberUserUuid, String adminUserUuid) {

  private static final int MINIMUM_API_KEY_LENGTH = 32;

  public QaTokenProperties {
    if (apiKey == null || apiKey.isBlank() || apiKey.length() < MINIMUM_API_KEY_LENGTH) {
      throw new IllegalArgumentException("qa.auth.api-key must contain at least 32 characters");
    }
    memberUserUuid = requireUuid("qa.auth.member-user-uuid", memberUserUuid);
    adminUserUuid = requireUuid("qa.auth.admin-user-uuid", adminUserUuid);
    if (memberUserUuid.equals(adminUserUuid)) {
      throw new IllegalArgumentException("QA MEMBER and ADMIN user UUIDs must be different");
    }
  }

  public String userUuid(Role role) {
    return switch (role) {
      case MEMBER -> memberUserUuid;
      case ADMIN -> adminUserUuid;
    };
  }

  @Override
  public String toString() {
    return "QaTokenProperties[apiKey=[REDACTED], memberUserUuid=[REDACTED], "
        + "adminUserUuid=[REDACTED]]";
  }

  private static String requireUuid(String propertyName, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(propertyName + " must be configured");
    }
    try {
      return UUID.fromString(value).toString();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(propertyName + " must be a valid UUID", exception);
    }
  }
}
