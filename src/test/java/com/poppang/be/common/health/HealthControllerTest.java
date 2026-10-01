package com.poppang.be.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.config.OpenApiConfig;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    classes = HealthControllerTest.TestApplication.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=true",
      "springdoc.api-docs.path=/test-api-docs",
      "springdoc.swagger-ui.enabled=false",
      "internal.worker.api-key=${random.uuid}${random.uuid}"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext applicationContext;

  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @Test
  void returnsUncachedHealthWithoutAuthenticationOrDatabaseAndRedis() throws Exception {
    assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
    assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class)).isEmpty();

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"isHealthy\":true}", JsonCompareMode.STRICT))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

    verifyNoInteractions(jwtProvider, usersRepository, authRateLimiter);
  }

  @Test
  void expiredOrInvalidBearerTokenDoesNotBlockHealthCheck() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-or-expired-token"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"isHealthy\":true}", JsonCompareMode.STRICT));

    verifyNoInteractions(jwtProvider, usersRepository, authRateLimiter);
  }

  @Test
  void swaggerPublishesHealthResponseInV1Only() throws Exception {
    mockMvc
        .perform(get("/test-api-docs/v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/health'].get").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/health'].get.responses['200'].content['application/json'].schema['$ref']")
                .value("#/components/schemas/HealthResponseDto"))
        .andExpect(
            jsonPath("$.components.schemas.HealthResponseDto.properties.isHealthy.type")
                .value("boolean"));

    mockMvc
        .perform(get("/test-api-docs/v2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/health']").doesNotExist());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @ComponentScan(
      basePackages = "com.poppang.be.common.health",
      useDefaultFilters = false,
      includeFilters = @ComponentScan.Filter(RestController.class))
  @Import({SecurityConfig.class, OpenApiConfig.class})
  static class TestApplication {}
}
