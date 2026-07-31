package com.poppang.be.domain.auth.application;

import com.poppang.be.domain.users.entity.Provider;
import java.util.Objects;

public record VerifiedSocialIdentity(Provider provider, String uid, String verifiedEmail) {

  public VerifiedSocialIdentity {
    Objects.requireNonNull(provider, "provider must not be null");
    if (uid == null || uid.isBlank()) {
      throw new IllegalArgumentException("uid must not be blank");
    }
    if (verifiedEmail != null && verifiedEmail.isBlank()) {
      verifiedEmail = null;
    }
  }
}
