package com.poppang.be.domain.auth.kakao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.auth.kakao.config.KakaoProperties;
import com.poppang.be.domain.users.entity.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class KakaoCredentialVerifierTest {

  private static final String TOKEN_URI = "https://mock.kakao.test/oauth/token";
  private static final String USER_INFO_URI = "https://mock.kakao.test/user/me";

  private MockRestServiceServer server;
  private KakaoCredentialVerifier verifier;

  @BeforeEach
  void setUp() {
    KakaoProperties properties = new KakaoProperties();
    properties.setClientId("test-client");
    properties.setRedirectUri("https://example.test/callback");
    properties.setTokenUri(TOKEN_URI);
    properties.setUserInfoUri(USER_INFO_URI);
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    verifier = new KakaoCredentialVerifier(properties, restTemplate);
  }

  @Test
  void mobileCredentialReturnsOnlyServerVerifiedKakaoIdentity() {
    server
        .expect(once(), requestTo(USER_INFO_URI))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {
                  "id": 12345,
                  "kakao_account": {
                    "email": "verified@example.com",
                    "is_email_valid": true,
                    "is_email_verified": true
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    VerifiedSocialIdentity identity = verifier.verifyMobileCredential("provider-access-token");

    assertThat(identity.provider()).isEqualTo(Provider.KAKAO);
    assertThat(identity.uid()).isEqualTo("12345");
    assertThat(identity.verifiedEmail()).isEqualTo("verified@example.com");
    assertThat(identity.toString()).doesNotContain("provider-access-token");
    server.verify();
  }

  @Test
  void unverifiedOrMissingEmailIsNotTrusted() {
    server
        .expect(once(), requestTo(USER_INFO_URI))
        .andRespond(
            withSuccess(
                """
                {
                  "id": 12345,
                  "kakao_account": {
                    "email": "unverified@example.com",
                    "is_email_valid": true,
                    "is_email_verified": false
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    assertThat(verifier.verifyMobileCredential("provider-access-token").verifiedEmail()).isNull();
    server.verify();
  }

  @Test
  void blankCredentialAndEmptyProviderResponseFailWithoutCallingARealProvider() {
    assertThatThrownBy(() -> verifier.verifyMobileCredential(" "))
        .isInstanceOf(BaseException.class);

    server
        .expect(once(), requestTo(USER_INFO_URI))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> verifier.verifyMobileCredential("provider-access-token"))
        .isInstanceOf(BaseException.class);
    server.verify();
  }

  @Test
  void providerHttpErrorIsMappedWithoutCallingAnyRealEndpoint() {
    server.expect(once(), requestTo(USER_INFO_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> verifier.verifyMobileCredential("provider-access-token"))
        .isInstanceOf(BaseException.class);
    server.verify();
  }

  @Test
  void webAuthorizationCodeIsExchangedBeforeTheSameIdentityVerification() {
    server
        .expect(once(), requestTo(TOKEN_URI))
        .andExpect(method(POST))
        .andRespond(
            withSuccess(
                """
                {"access_token":"provider-access-token","token_type":"bearer"}
                """,
                MediaType.APPLICATION_JSON));
    server
        .expect(once(), requestTo(USER_INFO_URI))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {"id":12345,"kakao_account":{}}
                """,
                MediaType.APPLICATION_JSON));

    VerifiedSocialIdentity identity = verifier.verifyWebAuthorizationCode("authorization-code");

    assertThat(identity.provider()).isEqualTo(Provider.KAKAO);
    assertThat(identity.uid()).isEqualTo("12345");
    server.verify();
  }
}
