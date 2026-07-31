package com.poppang.be.domain.auth.application;

public interface ProviderCredentialVerifier {

  VerifiedSocialIdentity verifyMobileCredential(String credential);

  default VerifiedSocialIdentity verifyMobileCredential(String credential, String rawNonce) {
    return verifyMobileCredential(credential);
  }

  VerifiedSocialIdentity verifyWebAuthorizationCode(String authorizationCode);
}
