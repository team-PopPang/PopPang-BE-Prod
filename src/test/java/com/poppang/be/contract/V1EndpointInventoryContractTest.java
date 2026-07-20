package com.poppang.be.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

class V1EndpointInventoryContractTest {

  private static final String DOMAIN_BASE_PACKAGE = "com.poppang.be.domain";
  private static final String CONTRACT_RESOURCE = "contracts/v1-endpoints.txt";
  private static final Set<String> APPROVED_DELETIONS =
      Set.of("POST /api/v1/auth/token/test", "POST /api/v1/auth/refresh");

  @Test
  void v1EndpointsMatchTheApprovedContract() throws Exception {
    List<String> expected = readContract();
    List<String> required = expected.stream().filter(line -> line.startsWith("KEEP|")).toList();
    List<String> approvedDeletions =
        expected.stream().filter(line -> line.startsWith("DELETE_APPROVED|")).toList();
    List<String> actual = findV1Endpoints();

    assertThat(expected).hasSize(78).doesNotHaveDuplicates();
    assertThat(required).hasSize(76);
    assertThat(approvedDeletions).hasSize(2);
    assertThat(actual).doesNotHaveDuplicates();
    assertThat(actual).containsAll(required);
    assertThat(actual).allMatch(expected::contains, "등록되지 않은 v1 endpoint가 없어야 한다");
  }

  private List<String> readContract() throws Exception {
    ClassPathResource resource = new ClassPathResource(CONTRACT_RESOURCE);
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isBlank())
          .filter(line -> !line.startsWith("#"))
          .sorted()
          .toList();
    }
  }

  private List<String> findV1Endpoints() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    return scanner.findCandidateComponents(DOMAIN_BASE_PACKAGE).stream()
        .map(BeanDefinition::getBeanClassName)
        .filter(Objects::nonNull)
        .filter(className -> !className.contains("$"))
        .map(this::loadClass)
        .flatMap(controller -> endpointsOf(controller).stream())
        .filter(endpoint -> endpoint.path().startsWith("/api/v1"))
        .map(this::toContractLine)
        .sorted()
        .toList();
  }

  private Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new AssertionError("Controller class를 불러올 수 없습니다: " + className, e);
    }
  }

  private List<Endpoint> endpointsOf(Class<?> controller) {
    RequestMapping classMapping =
        AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
    List<String> basePaths = pathsOf(classMapping);
    List<Endpoint> endpoints = new ArrayList<>();

    for (Method method : controller.getDeclaredMethods()) {
      RequestMapping methodMapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (methodMapping == null || methodMapping.method().length == 0) {
        continue;
      }

      String[] consumes = effectiveMediaTypes(classMapping, methodMapping, true);
      String[] produces = effectiveMediaTypes(classMapping, methodMapping, false);
      for (String basePath : basePaths) {
        for (String methodPath : pathsOf(methodMapping)) {
          for (RequestMethod requestMethod : methodMapping.method()) {
            endpoints.add(
                new Endpoint(
                    requestMethod.name(),
                    combinePaths(basePath, methodPath),
                    formatMediaTypes(consumes),
                    formatMediaTypes(produces)));
          }
        }
      }
    }

    return endpoints;
  }

  private List<String> pathsOf(RequestMapping mapping) {
    if (mapping == null) {
      return List.of("");
    }
    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? List.of("") : Arrays.asList(paths);
  }

  private String[] effectiveMediaTypes(
      RequestMapping classMapping, RequestMapping methodMapping, boolean consumes) {
    String[] methodValues = consumes ? methodMapping.consumes() : methodMapping.produces();
    if (methodValues.length > 0 || classMapping == null) {
      return methodValues;
    }
    return consumes ? classMapping.consumes() : classMapping.produces();
  }

  private String combinePaths(String basePath, String methodPath) {
    String combined = (basePath + "/" + methodPath).replaceAll("/{2,}", "/");
    if (!combined.startsWith("/")) {
      combined = "/" + combined;
    }
    if (combined.length() > 1 && combined.endsWith("/")) {
      combined = combined.substring(0, combined.length() - 1);
    }
    return combined;
  }

  private String formatMediaTypes(String[] mediaTypes) {
    if (mediaTypes.length == 0) {
      return "-";
    }
    return Arrays.stream(mediaTypes)
        .sorted()
        .reduce((left, right) -> left + "," + right)
        .orElse("-");
  }

  private String toContractLine(Endpoint endpoint) {
    String operation = endpoint.httpMethod() + " " + endpoint.path();
    String status = APPROVED_DELETIONS.contains(operation) ? "DELETE_APPROVED" : "KEEP";
    return String.join(
        "|",
        status,
        endpoint.httpMethod(),
        endpoint.path(),
        "consumes=" + endpoint.consumes(),
        "produces=" + endpoint.produces());
  }

  private record Endpoint(String httpMethod, String path, String consumes, String produces) {}
}
