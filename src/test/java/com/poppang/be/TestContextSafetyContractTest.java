package com.poppang.be;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfiles;

class TestContextSafetyContractTest {

  @Test
  void contextSmokeTestCannotUseConfiguredDatabaseOrRedis() throws Exception {
    Class<?> smokeTest = Class.forName("com.poppang.be.PoppangBeApplicationTests");
    SpringBootTest springBootTest = smokeTest.getAnnotation(SpringBootTest.class);

    assertThat(springBootTest.classes()).hasSize(1);

    EnableAutoConfiguration autoConfiguration =
        springBootTest.classes()[0].getAnnotation(EnableAutoConfiguration.class);

    assertThat(Arrays.asList(autoConfiguration.exclude()))
        .contains(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class);
  }

  @Test
  void contextSmokeTestUsesExplicitTestProfileWithoutStartingAWebServer() throws Exception {
    Class<?> smokeTest = Class.forName("com.poppang.be.PoppangBeApplicationTests");
    SpringBootTest springBootTest = smokeTest.getAnnotation(SpringBootTest.class);
    ActiveProfiles activeProfiles = smokeTest.getAnnotation(ActiveProfiles.class);

    assertThat(activeProfiles).isNotNull();
    assertThat(activeProfiles.value()).containsExactly("test");
    assertThat(springBootTest.webEnvironment()).isEqualTo(SpringBootTest.WebEnvironment.NONE);
  }

  @Test
  void v1SecuritySliceUsesTestProfileAndDisablesFullAutoConfiguration() throws Exception {
    Class<?> securityTest =
        Class.forName("com.poppang.be.common.security.V1SecurityCompatibilityTest");
    ActiveProfiles activeProfiles = securityTest.getAnnotation(ActiveProfiles.class);
    WebMvcTest webMvcTest = securityTest.getAnnotation(WebMvcTest.class);
    OverrideAutoConfiguration overrideAutoConfiguration =
        AnnotatedElementUtils.findMergedAnnotation(securityTest, OverrideAutoConfiguration.class);

    assertThat(activeProfiles).isNotNull();
    assertThat(activeProfiles.value()).containsExactly("test");
    assertThat(webMvcTest).isNotNull();
    assertThat(overrideAutoConfiguration).isNotNull();
    assertThat(overrideAutoConfiguration.enabled()).isFalse();
  }
}
