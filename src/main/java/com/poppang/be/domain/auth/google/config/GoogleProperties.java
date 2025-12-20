package com.poppang.be.domain.auth.google.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "google")
public class GoogleProperties {

  private String clientId;
  private String iosClientId;
  private String clientSecret;
  private String redirectUri;
  private String scope;
  private String tokenUri;
  private String userInfoUri;
}
