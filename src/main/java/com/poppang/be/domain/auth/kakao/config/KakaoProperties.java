package com.poppang.be.domain.auth.kakao.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kakao")
public class KakaoProperties {

  private String clientId;
  private String redirectUri;
  private String tokenUri;
  private String userInfoUri;
}
