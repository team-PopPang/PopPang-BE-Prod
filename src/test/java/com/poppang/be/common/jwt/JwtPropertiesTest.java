package com.poppang.be.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class JwtPropertiesTest {

  private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

  @Test
  void rejectsSecretShorterThan256Bits() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties("1234567890123456789012345678901", 15, 30, "poppang"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(" ".repeat(32), 15, 30, "poppang"));
  }

  @Test
  void rejectsBlankIssuer() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(VALID_SECRET, 15, 30, " "));
  }

  @Test
  void doesNotExposeSecretFromToString() {
    JwtProperties properties = new JwtProperties(VALID_SECRET, 15, 30, "poppang");

    assertThat(properties.toString()).doesNotContain(VALID_SECRET).contains("[REDACTED]");
  }

  @Test
  void exposesSeparateV2AudienceAndSignupExpirationSettings() {
    assertThat(
            Arrays.stream(JwtProperties.class.getRecordComponents())
                .map(component -> component.getName()))
        .contains("audience", "signupAudience", "signupTokenExpMinutes");
  }

  @Test
  void rejectsMissingAudiencesAndNonPositiveExpirations() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(VALID_SECRET, 15, 30, "poppang", " ", "signup", 15));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(VALID_SECRET, 15, 30, "poppang", "app", null, 15));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(VALID_SECRET, 15, 30, "poppang", "same", "same", 15));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(VALID_SECRET, 0, 30, "poppang", "app", "signup", 15));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(VALID_SECRET, 15, 0, "poppang", "app", "signup", 15));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(VALID_SECRET, 15, 30, "poppang", "app", "signup", 0));
  }

  @Test
  void bindsAllRequiredV2SettingsByPropertyName() {
    MapConfigurationPropertySource source = new MapConfigurationPropertySource();
    source.put("jwt.secret", VALID_SECRET);
    source.put("jwt.issuer", "poppang");
    source.put("jwt.audience", "poppang-app-v2");
    source.put("jwt.signup-audience", "poppang-signup-v2");
    source.put("jwt.access-token-exp-minutes", 15);
    source.put("jwt.refresh-token-exp-days", 30);
    source.put("jwt.signup-token-exp-minutes", 15);

    JwtProperties properties =
        new Binder(source)
            .bind("jwt", Bindable.of(JwtProperties.class))
            .orElseThrow(() -> new AssertionError("JWT properties were not bound"));

    assertThat(properties.issuer()).isEqualTo("poppang");
    assertThat(properties.audience()).isEqualTo("poppang-app-v2");
    assertThat(properties.signupAudience()).isEqualTo("poppang-signup-v2");
    assertThat(properties.accessTokenExpMinutes()).isEqualTo(15);
    assertThat(properties.refreshTokenExpDays()).isEqualTo(30);
    assertThat(properties.signupTokenExpMinutes()).isEqualTo(15);
  }
}
