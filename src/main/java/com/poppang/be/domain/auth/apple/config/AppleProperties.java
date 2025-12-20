package com.poppang.be.domain.auth.apple.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "apple")
public class AppleProperties {

  private String clientId;
  private String teamId;
  private String keyId;
  private String privateKeyPath;
  private String redirectUri;
  private String tokenUri;
}
