package com.poppang.be.domain.auth.google.application;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.auth.application.ProviderCredentialVerifier;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.auth.google.config.GoogleProperties;
import com.poppang.be.domain.auth.google.dto.response.GoogleTokenResponseDto;
import com.poppang.be.domain.auth.google.dto.response.GoogleUserInfoResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
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
public class GoogleCredentialVerifier implements ProviderCredentialVerifier {

  private static final List<String> ALLOWED_ISSUERS =
      List.of("accounts.google.com", "https://accounts.google.com");

  private final GoogleProperties properties;
  private final RestOperations restOperations;
  private final GoogleIdTokenValidator idTokenValidator;
  private final Clock clock;

  @Autowired
  public GoogleCredentialVerifier(
      GoogleProperties properties, RestTemplateBuilder restTemplateBuilder) {
    this(
        properties,
        restTemplateBuilder.build(),
        credential -> productionVerifier(properties).verify(credential),
        Clock.systemUTC());
  }

  GoogleCredentialVerifier(
      GoogleProperties properties,
      RestOperations restOperations,
      GoogleIdTokenValidator idTokenValidator,
      Clock clock) {
    this.properties = properties;
    this.restOperations = restOperations;
    this.idTokenValidator = idTokenValidator;
    this.clock = clock;
  }

  @Override
  public VerifiedSocialIdentity verifyMobileCredential(String credential) {
    requireCredential(credential);
    try {
      GoogleIdToken token = idTokenValidator.verify(credential);
      if (token == null) {
        throw invalidCredential();
      }
      GoogleIdToken.Payload payload = token.getPayload();
      validatePayload(payload);
      String verifiedEmail =
          Boolean.TRUE.equals(payload.getEmailVerified()) ? nonBlank(payload.getEmail()) : null;
      return new VerifiedSocialIdentity(Provider.GOOGLE, payload.getSubject(), verifiedEmail);
    } catch (BaseException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalidCredential();
    }
  }

  @Override
  public VerifiedSocialIdentity verifyWebAuthorizationCode(String authorizationCode) {
    requireCredential(authorizationCode);
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("grant_type", "authorization_code");
      form.add("code", authorizationCode);
      form.add("client_id", properties.getClientId());
      form.add("client_secret", properties.getClientSecret());
      form.add("redirect_uri", properties.getRedirectUri());

      ResponseEntity<GoogleTokenResponseDto> tokenResponse =
          restOperations.exchange(
              properties.getTokenUri(),
              HttpMethod.POST,
              new HttpEntity<>(form, headers),
              GoogleTokenResponseDto.class);
      GoogleTokenResponseDto token = tokenResponse.getBody();
      if (token == null || nonBlank(token.getAccessToken()) == null) {
        throw invalidCredential();
      }

      HttpHeaders userInfoHeaders = new HttpHeaders();
      userInfoHeaders.setBearerAuth(token.getAccessToken());
      userInfoHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
      ResponseEntity<GoogleUserInfoResponseDto> userInfoResponse =
          restOperations.exchange(
              properties.getUserInfoUri(),
              HttpMethod.GET,
              new HttpEntity<>(userInfoHeaders),
              GoogleUserInfoResponseDto.class);
      GoogleUserInfoResponseDto userInfo = userInfoResponse.getBody();
      if (userInfo == null || nonBlank(userInfo.getSub()) == null) {
        throw invalidCredential();
      }
      return new VerifiedSocialIdentity(
          Provider.GOOGLE, userInfo.getSub(), nonBlank(userInfo.getEmail()));
    } catch (BaseException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw invalidCredential();
    }
  }

  private void validatePayload(GoogleIdToken.Payload payload) {
    if (payload == null
        || !ALLOWED_ISSUERS.contains(payload.getIssuer())
        || payload.getAudienceAsList().stream().noneMatch(allowedAudiences()::contains)
        || payload.getExpirationTimeSeconds() == null
        || payload.getExpirationTimeSeconds() <= clock.instant().getEpochSecond()
        || nonBlank(payload.getSubject()) == null) {
      throw invalidCredential();
    }
  }

  private List<String> allowedAudiences() {
    List<String> audiences =
        Stream.of(properties.getClientId(), properties.getIosClientId())
            .map(this::nonBlank)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (audiences.isEmpty()) {
      throw invalidCredential();
    }
    return audiences;
  }

  private static GoogleIdTokenVerifier productionVerifier(GoogleProperties properties) {
    List<String> audiences =
        Stream.of(properties.getClientId(), properties.getIosClientId())
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new JacksonFactory())
        .setAudience(audiences)
        .setIssuers(ALLOWED_ISSUERS)
        .build();
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
  interface GoogleIdTokenValidator {
    GoogleIdToken verify(String credential) throws Exception;
  }
}
