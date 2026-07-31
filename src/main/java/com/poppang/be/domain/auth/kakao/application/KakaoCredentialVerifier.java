package com.poppang.be.domain.auth.kakao.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.auth.application.ProviderCredentialVerifier;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.auth.kakao.config.KakaoProperties;
import com.poppang.be.domain.auth.kakao.dto.response.KakaoTokenResponseDto;
import com.poppang.be.domain.auth.kakao.dto.response.KakaoUserInfoResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import java.util.List;
import java.util.Map;
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
public class KakaoCredentialVerifier implements ProviderCredentialVerifier {

  private final KakaoProperties properties;
  private final RestOperations restOperations;

  @Autowired
  public KakaoCredentialVerifier(
      KakaoProperties properties, RestTemplateBuilder restTemplateBuilder) {
    this(properties, restTemplateBuilder.build());
  }

  KakaoCredentialVerifier(KakaoProperties properties, RestOperations restOperations) {
    this.properties = properties;
    this.restOperations = restOperations;
  }

  @Override
  public VerifiedSocialIdentity verifyMobileCredential(String credential) {
    requireCredential(credential);
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(credential);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));
      ResponseEntity<KakaoUserInfoResponseDto> response =
          restOperations.exchange(
              properties.getUserInfoUri(),
              HttpMethod.GET,
              new HttpEntity<>(headers),
              KakaoUserInfoResponseDto.class);
      KakaoUserInfoResponseDto body = response.getBody();
      if (body == null || body.getId() == null) {
        throw invalidCredential();
      }
      return new VerifiedSocialIdentity(
          Provider.KAKAO, String.valueOf(body.getId()), verifiedEmail(body.getKakaoAccount()));
    } catch (BaseException exception) {
      throw exception;
    } catch (RestClientException exception) {
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
      form.add("client_id", properties.getClientId());
      form.add("redirect_uri", properties.getRedirectUri());
      form.add("code", authorizationCode);

      ResponseEntity<KakaoTokenResponseDto> response =
          restOperations.exchange(
              properties.getTokenUri(),
              HttpMethod.POST,
              new HttpEntity<>(form, headers),
              KakaoTokenResponseDto.class);
      KakaoTokenResponseDto body = response.getBody();
      if (body == null || body.getAccessToken() == null || body.getAccessToken().isBlank()) {
        throw invalidCredential();
      }
      return verifyMobileCredential(body.getAccessToken());
    } catch (BaseException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw invalidCredential();
    }
  }

  private String verifiedEmail(Map<String, Object> account) {
    if (account == null
        || !Boolean.TRUE.equals(account.get("is_email_valid"))
        || !Boolean.TRUE.equals(account.get("is_email_verified"))) {
      return null;
    }
    Object email = account.get("email");
    return email instanceof String value && !value.isBlank() ? value : null;
  }

  private void requireCredential(String credential) {
    if (credential == null || credential.isBlank()) {
      throw invalidCredential();
    }
  }

  private BaseException invalidCredential() {
    return new BaseException(ErrorCode.INVALID_SOCIAL_CREDENTIAL);
  }
}
