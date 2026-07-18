package com.poppang.be;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = PoppangBeApplicationTests.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.config.location=classpath:/application-test.yml")
@ActiveProfiles("test")
class PoppangBeApplicationTests {

  private static final Set<String> LOOPBACK_HOSTS =
      Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
  private static final List<String> EXTERNAL_ENDPOINT_PROPERTIES =
      List.of(
          "spring.datasource.url",
          "spring.datasource.hikari.jdbc-url",
          "spring.data.redis.host",
          "spring.data.redis.url",
          "spring.redis.host",
          "spring.redis.url");

  @Autowired private ApplicationContext applicationContext;
  @Autowired private ConfigurableEnvironment environment;

  @Test
  void isolatedContextLoads() {
    assertThat(applicationContext).isNotNull();
  }

  @Test
  void isolatedContextUsesOnlyTestProfileAndTestConfig() {
    assertThat(environment.getActiveProfiles()).containsExactly("test");
    assertThat(environment.acceptsProfiles(Profiles.of("prod"))).isFalse();
    assertThat(environment.getProperty("spring.config.location"))
        .isEqualTo("classpath:/application-test.yml");

    List<String> propertySourceNames =
        StreamSupport.stream(environment.getPropertySources().spliterator(), false)
            .map(PropertySource::getName)
            .toList();

    assertThat(propertySourceNames).anyMatch(name -> name.contains("application-test.yml"));
    assertThat(propertySourceNames)
        .noneMatch(
            name ->
                name.contains("[application.yml]")
                    || name.contains("[application-prod.yml]")
                    || name.contains("[application-dev.yml]")
                    || name.contains("[application-local.yml]"));
  }

  @Test
  void isolatedContextHasNoDatabaseJpaOrRedisInfrastructure() {
    assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
    assertThat(applicationContext.getBeansOfType(EntityManagerFactory.class)).isEmpty();
    assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
    assertThat(applicationContext.getBeansOfType(RedisTemplate.class)).isEmpty();
  }

  @Test
  void databaseAndRedisEndpointsAreAbsentOrLoopbackOnly() {
    List<String> nonLoopbackProperties =
        EXTERNAL_ENDPOINT_PROPERTIES.stream()
            .filter(
                propertyName -> {
                  String value = environment.getProperty(propertyName);
                  return value != null && !value.isBlank() && !isLoopbackEndpoint(value);
                })
            .toList();

    assertThat(nonLoopbackProperties)
        .as("Database and Redis endpoints must be absent or use loopback addresses")
        .isEmpty();
  }

  private boolean isLoopbackEndpoint(String value) {
    String candidate = value.trim();
    if (candidate.regionMatches(true, 0, "jdbc:", 0, "jdbc:".length())) {
      candidate = candidate.substring("jdbc:".length());
    }

    if (!candidate.contains("://")) {
      return isLoopbackHost(candidate);
    }

    try {
      return isLoopbackHost(URI.create(candidate).getHost());
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private boolean isLoopbackHost(String host) {
    if (host == null) {
      return false;
    }

    String normalized = host.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    return LOOPBACK_HOSTS.contains(normalized);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
      })
  static class TestApplication {}
}
