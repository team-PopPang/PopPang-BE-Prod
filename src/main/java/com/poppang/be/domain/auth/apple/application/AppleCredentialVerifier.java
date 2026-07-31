package com.poppang.be.domain.auth.apple.application;

import com.nimbusds.jwt.JWTClaimsSet;
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtFingerprint;
import com.poppang.be.domain.auth.apple.config.AppleProperties;
import com.poppang.be.domain.auth.apple.dto.response.AppleTokenResponseDto;
import com.poppang.be.domain.auth.apple.util.AppleJwtUtil;
import com.poppang.be.domain.auth.apple.util.AppleJwtVerifier;
import com.poppang.be.domain.auth.application.ProviderCredentialVerifier;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.users.entity.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

@Component
public class AppleCredentialVerifier implements ProviderCredentialVerifier {

  private static final String APPLE_ISSUER = "https://appleid.apple.com";

  private final AppleProperties properties;
  private final RestOperations restOperations;
  private final AppleIdTokenValidator idTokenValidator;
  private final AppleClientSecretProvider clientSecretProvider;
  private final Clock clock;

  @Autowired
  public AppleCredentialVerifier(
      AppleProperties properties, RestTemplateBuilder restTemplateBuilder) {
    this(
        properties,
        restTemplateBuilder.build(),
        AppleJwtVerifier::verifyIdToken,
        () -> AppleJwtUtil.createClientSecret(properties),
        Clock.systemUTC());
  }

  AppleCredentialVerifier(
      AppleProperties properties,
      RestOperations restOperations,
      AppleIdTokenValidator idTokenValidator,
      AppleClientSecretProvider clientSecretProvider,
      Clock clock) {
    this.properties = properties;
    this.restOperations = restOperations;
    this.idTokenValidator = idTokenValidator;
    this.clientSecretProvider = clientSecretProvider;
    this.clock = clock;
  }

  @Override
  public VerifiedSocialIdentity verifyMobileCredential(String credential) {
    throw invalidCredential();
  }

  @Override
  public VerifiedSocialIdentity verifyMobileCredential(String credential, String rawNonce) {
    return verifyAuthorizationCode(credential, rawNonce);
  }

  @Override
  public VerifiedSocialIdentity verifyWebAuthorizationCode(String authorizationCode) {
    throw invalidCredential();
  }

  private VerifiedSocialIdentity verifyAuthorizationCode(
      String authorizationCode, String rawNonce) {
    requireCredential(authorizationCode);
    requireCredential(rawNonce);
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("client_id", properties.getClientId());
      form.add("client_secret", clientSecretProvider.create());
      form.add("code", authorizationCode);
      form.add("grant_type", "authorization_code");
      form.add("redirect_uri", properties.getRedirectUri());

      ResponseEntity<AppleTokenResponseDto> response =
          restOperations.exchange(
              properties.getTokenUri(),
              HttpMethod.POST,
              new HttpEntity<>(form, headers),
              AppleTokenResponseDto.class);
      AppleTokenResponseDto token = response.getBody();
      if (token == null
          || nonBlank(token.getAccessToken()) == null
          || nonBlank(token.getIdToken()) == null) {
        throw invalidCredential();
      }

      JWTClaimsSet claims = idTokenValidator.verify(token.getIdToken(), properties.getClientId());
      validateClaims(claims, rawNonce);
      return new VerifiedSocialIdentity(Provider.APPLE, claims.getSubject(), verifiedEmail(claims));
    } catch (BaseException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw invalidCredential();
    } catch (Exception exception) {
      throw invalidCredential();
    }
  }

  private void validateClaims(JWTClaimsSet claims, String rawNonce) {
    Date expirationTime = claims == null ? null : claims.getExpirationTime();
    if (claims == null
        || !APPLE_ISSUER.equals(claims.getIssuer())
        || claims.getAudience() == null
        || !claims.getAudience().contains(properties.getClientId())
        || expirationTime == null
        || !expirationTime.toInstant().isAfter(clock.instant())
        || nonBlank(claims.getSubject()) == null
        || !nonceMatches(claims, rawNonce)) {
      throw invalidCredential();
    }
  }

  private boolean nonceMatches(JWTClaimsSet claims, String rawNonce) {
    String tokenNonce = nonBlank((String) claims.getClaim("nonce"));
    if (tokenNonce == null) {
      return false;
    }
    byte[] expected = JwtFingerprint.sha256(rawNonce).getBytes(StandardCharsets.UTF_8);
    byte[] actual = tokenNonce.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, actual);
  }

  private String verifiedEmail(JWTClaimsSet claims) {
    Object verified = claims.getClaim("email_verified");
    boolean emailVerified =
        Boolean.TRUE.equals(verified)
            || (verified instanceof String value && Boolean.parseBoolean(value));
    return emailVerified ? nonBlank((String) claims.getClaim("email")) : null;
  }

  private void requireCredential(String credential) {
    if (nonBlank(credential) == null) {
      throw invalidCredential();
    }
  }

  private String nonBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private BaseException invalidCredential() {
    return new BaseException(ErrorCode.INVALID_SOCIAL_CREDENTIAL);
  }

  @FunctionalInterface
  interface AppleIdTokenValidator {
    JWTClaimsSet verify(String idToken, String clientId) throws Exception;
  }

  @FunctionalInterface
  interface AppleClientSecretProvider {
    String create() throws Exception;
  }
}
