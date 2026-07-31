package com.poppang.be.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poppang.be.domain.users.entity.Provider;
import org.junit.jupiter.api.Test;

class VerifiedSocialIdentityTest {

  @Test
  void keepsOnlyProviderUidAndNullableVerifiedEmail() {
    VerifiedSocialIdentity identity =
        new VerifiedSocialIdentity(Provider.KAKAO, "provider-user", " ");

    assertThat(identity.provider()).isEqualTo(Provider.KAKAO);
    assertThat(identity.uid()).isEqualTo("provider-user");
    assertThat(identity.verifiedEmail()).isNull();
  }

  @Test
  void rejectsMissingProviderIdentity() {
    assertThatThrownBy(() -> new VerifiedSocialIdentity(Provider.KAKAO, " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
