package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    classes = V1ApiCompatibilityContractTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "internal.worker.api-key=${random.uuid}${random.uuid}"
    })
@ActiveProfiles("test")
class V1ApiCompatibilityContractTest {

  private static final Set<Endpoint> APPROVED_V1_ENDPOINTS =
      Set.of(
          // 알림
          endpoint(POST, "/api/v1/users/{userUuid}/alert"),
          endpoint(DELETE, "/api/v1/users/{userUuid}/alert"),
          endpoint(GET, "/api/v1/users/{userUuid}/alert/popups"),
          endpoint(PATCH, "/api/v1/users/{userUuid}/alert/read"),

          // 인증: 청크 5에서 삭제 승인된 실험용 token/refresh 두 endpoint는 제외함.
          endpoint(GET, "/api/v1/auth/kakao/login"),
          endpoint(GET, "/api/v1/auth/apple/login"),
          endpoint(GET, "/api/v1/auth/google/login"),
          endpoint(POST, "/api/v1/auth/kakao/mobile/login"),
          endpoint(POST, "/api/v1/auth/apple/mobile/login"),
          endpoint(POST, "/api/v1/auth/google/mobile/login"),
          endpoint(POST, "/api/v1/auth/autoLogin"),
          endpoint(POST, "/api/v1/auth/kakao/signup"),
          endpoint(POST, "/api/v1/auth/apple/signup"),
          endpoint(POST, "/api/v1/auth/google/signup"),

          // 즐겨찾기
          endpoint(POST, "/api/v1/favorite"),
          endpoint(DELETE, "/api/v1/favorite"),
          endpoint(GET, "/api/v1/favorite/count/{popupUuid}"),
          endpoint(GET, "/api/v1/favorite/popup/{userUuid}"),

          // 알림 키워드
          endpoint(GET, "/api/v1/alert-keyword"),
          endpoint(POST, "/api/v1/alert-keyword"),
          endpoint(DELETE, "/api/v1/alert-keyword"),

          // 관리자
          endpoint(PATCH, "/api/v1/admin/popup/{popupUuid}/deactivate"),
          endpoint(GET, "/api/v1/admin/popup-submissions"),
          endpoint(GET, "/api/v1/admin/popup-submissions/{popupSubmissionId}"),
          endpoint(PUT, "/api/v1/admin/popup-submissions/{popupSubmissionId}"),
          endpoint(PATCH, "/api/v1/admin/popup-submissions/{submissionId}/status"),

          // 비회원 팝업
          endpoint(GET, "/api/v1/popup"),
          endpoint(POST, "/api/v1/popup"),
          endpoint(GET, "/api/v1/popup/{popupUuid}"),
          endpoint(GET, "/api/v1/popup/search"),
          endpoint(GET, "/api/v1/popup/upcoming"),
          endpoint(GET, "/api/v1/popup/{userUuid}/recommend"),
          endpoint(GET, "/api/v1/popup/inProgress"),
          endpoint(GET, "/api/v1/popup/regions/districts"),
          endpoint(GET, "/api/v1/popup/filtered"),
          endpoint(GET, "/api/v1/popup/filtered/home"),
          endpoint(GET, "/api/v1/popup/filtered/map"),
          endpoint(GET, "/api/v1/popup/{popupUuid}/related"),
          endpoint(GET, "/api/v1/popup/random"),
          endpoint(GET, "/api/v1/popup/recommendations/{recommendId}"),
          endpoint(PUT, "/api/v1/popup/{popupUuid}/images"),
          endpoint(POST, "/api/v1/popup-submissions"),
          endpoint(POST, "/api/v1/popup/{popupUuid}/view"),
          endpoint(GET, "/api/v1/popup/{popupUuid}/total-view-count"),
          endpoint(GET, "/api/v1/popup/{popupUuid}/view-count"),

          // 회원 팝업
          endpoint(GET, "/api/v1/users/{userUuid}/popups"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/{popupUuid}"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/upcoming"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/recommend"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/search"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/inProgress"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/filtered/home"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/filtered/map"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/{popupUuid}/related"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/random"),
          endpoint(GET, "/api/v1/users/{userUuid}/popups/recommendations/{recommendId}"),

          // 웹 팝업
          endpoint(GET, "/api/v1/web/popup/random"),
          endpoint(GET, "/api/v1/web/popup/favorite"),
          endpoint(GET, "/api/v1/web/popup/in-progress"),
          endpoint(GET, "/api/v1/web/popup/upcoming"),
          endpoint(GET, "/api/v1/web/popup/search"),
          endpoint(GET, "/api/v1/web/popup/{popupUuid}"),

          // 추천
          endpoint(GET, "/api/v1/recommend"),
          endpoint(GET, "/api/v1/recommend/featured"),
          endpoint(GET, "/api/v1/recommend/web"),

          // 사용자
          endpoint(GET, "/api/v1/user/{userUuid}"),
          endpoint(PATCH, "/api/v1/user/{userUuid}/alert-status"),
          endpoint(GET, "/api/v1/user/nickname/duplicated"),
          endpoint(PATCH, "/api/v1/user/{userUuid}"),
          endpoint(DELETE, "/api/v1/user/{userUuid}/hard-delete"),
          endpoint(PATCH, "/api/v1/user/{userUuid}/soft-delete"),
          endpoint(PATCH, "/api/v1/user/{userUuid}/resotre"),
          endpoint(GET, "/api/v1/user/{userUuid}/fcm-token/duplicate-check"),
          endpoint(PUT, "/api/v1/user/{userUuid}/fcm-token/update"),
          endpoint(GET, "/api/v1/user/with-alert-keyword/a"),
          endpoint(GET, "/api/v1/user/with-alert-keyword/b"));

  @Autowired private FilterChainProxy springSecurityFilterChain;

  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @Test
  void applicationV1MappingsMatchApprovedInventory() throws Exception {
    Set<Endpoint> actualEndpoints = discoverApplicationV1Endpoints();

    assertThat(APPROVED_V1_ENDPOINTS).hasSize(76);
    assertThat(actualEndpoints)
        .as("Any v1 endpoint addition, removal, method change, or path change requires approval")
        .containsExactlyInAnyOrderElementsOf(APPROVED_V1_ENDPOINTS);
  }

  @Test
  void anonymousRequestsReachTheApplicationSideOfTheSecurityFilterChain() throws Exception {
    for (Endpoint endpoint : new TreeSet<>(APPROVED_V1_ENDPOINTS)) {
      String requestPath = endpoint.path().replaceAll("\\{[^/]+}", "contract-value");
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setMethod(endpoint.method().name());
      request.setRequestURI(requestPath);
      request.setServletPath(requestPath);
      MockHttpServletResponse response = new MockHttpServletResponse();
      AtomicBoolean reachedApplication = new AtomicBoolean(false);

      springSecurityFilterChain.doFilter(
          request,
          response,
          (servletRequest, servletResponse) -> {
            reachedApplication.set(true);
            ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NO_CONTENT);
          });

      assertThat(reachedApplication)
          .as("Anonymous %s must pass the security filter chain", endpoint)
          .isTrue();
      assertThat(response.getStatus())
          .as("Anonymous %s must not receive 401 or 403 from the security filter chain", endpoint)
          .isEqualTo(HttpServletResponse.SC_NO_CONTENT)
          .isNotIn(HttpServletResponse.SC_UNAUTHORIZED, HttpServletResponse.SC_FORBIDDEN);
    }

    verifyNoInteractions(jwtProvider, usersRepository);
  }

  private Set<Endpoint> discoverApplicationV1Endpoints() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    Set<Endpoint> endpoints = new TreeSet<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents("com.poppang.be")) {
      Class<?> controllerClass =
          ClassUtils.forName(candidate.getBeanClassName(), getClass().getClassLoader());
      addControllerEndpoints(controllerClass, endpoints);
    }
    endpoints.removeIf(endpoint -> !isV1Path(endpoint.path()));
    return endpoints;
  }

  private void addControllerEndpoints(Class<?> controllerClass, Set<Endpoint> endpoints) {
    RequestMapping controllerMapping =
        AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class);
    List<String> controllerPaths = mappingPaths(controllerMapping);

    for (Method method : controllerClass.getDeclaredMethods()) {
      RequestMapping methodMapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (methodMapping == null) {
        continue;
      }
      if (methodMapping.method().length == 0) {
        throw new IllegalStateException(
            "Every application endpoint must declare an HTTP method: "
                + controllerClass.getName()
                + "#"
                + method.getName());
      }

      for (String controllerPath : controllerPaths) {
        for (String methodPath : mappingPaths(methodMapping)) {
          for (RequestMethod requestMethod : methodMapping.method()) {
            endpoints.add(endpoint(requestMethod, combinePaths(controllerPath, methodPath)));
          }
        }
      }
    }
  }

  private List<String> mappingPaths(RequestMapping mapping) {
    if (mapping == null) {
      return List.of("");
    }

    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? List.of("") : new ArrayList<>(Arrays.asList(paths));
  }

  private String combinePaths(String controllerPath, String methodPath) {
    String combined = (controllerPath + "/" + methodPath).replaceAll("/{2,}", "/");
    if (!combined.startsWith("/")) {
      combined = "/" + combined;
    }
    if (combined.length() > 1 && combined.endsWith("/")) {
      combined = combined.substring(0, combined.length() - 1);
    }
    return combined;
  }

  private boolean isV1Path(String path) {
    return path.equals("/api/v1") || path.startsWith("/api/v1/");
  }

  private static Endpoint endpoint(RequestMethod method, String path) {
    return new Endpoint(method, path);
  }

  private record Endpoint(RequestMethod method, String path) implements Comparable<Endpoint> {

    @Override
    public int compareTo(Endpoint other) {
      int pathComparison = path.compareTo(other.path);
      return pathComparison != 0 ? pathComparison : method.compareTo(other.method);
    }

    @Override
    public String toString() {
      return method + " " + path;
    }
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
  @Import(SecurityConfig.class)
  static class TestApplication {}
}
