package com.poppang.be.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poppang.be.domain.users.entity.Role;
import org.junit.jupiter.api.Test;

class QaTokenPropertiesTest {

  private static final String API_KEY = "qa-api-key-0123456789-abcdefghijklmnop";
  private static final String MEMBER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String ADMIN_UUID = "22222222-2222-2222-2222-222222222222";

  @Test
  void mapsFixedRolesWithoutExposingConfigurationInToString() {
    QaTokenProperties properties = new QaTokenProperties(API_KEY, MEMBER_UUID, ADMIN_UUID);

    assertThat(properties.userUuid(Role.MEMBER)).isEqualTo(MEMBER_UUID);
    assertThat(properties.userUuid(Role.ADMIN)).isEqualTo(ADMIN_UUID);
    assertThat(properties.toString())
        .doesNotContain(API_KEY, MEMBER_UUID, ADMIN_UUID)
        .contains("[REDACTED]");
  }

  @Test
  void rejectsShortKeysInvalidUuidsAndSharedAccounts() {
    assertThatThrownBy(() -> new QaTokenProperties("short", MEMBER_UUID, ADMIN_UUID))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QaTokenProperties(API_KEY, "not-a-uuid", ADMIN_UUID))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QaTokenProperties(API_KEY, MEMBER_UUID, MEMBER_UUID))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
