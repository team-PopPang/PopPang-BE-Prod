package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class V2PopupCoreEndpointContractTest {

  private static final String CONTROLLER =
      "com.poppang.be.domain.popup.presentation.v2.V2PopupController";

  @Test
  void v2PopupCoreControllerExists() {
    assertThatCode(() -> Class.forName(CONTROLLER)).doesNotThrowAnyException();
  }

  @Test
  void v2PopupCoreKeepsTheSevenApprovedMappingsWithoutCallerUuid() throws Exception {
    assertThat(endpointsOf(CONTROLLER))
        .contains(
            "GET /api/v2/popup",
            "GET /api/v2/popup/{popupUuid}",
            "GET /api/v2/popup/search",
            "GET /api/v2/popup/upcoming",
            "GET /api/v2/popup/inProgress",
            "GET /api/v2/popup/regions/districts",
            "GET /api/v2/popup/random")
        .noneMatch(endpoint -> endpoint.toLowerCase().contains("useruuid"));
  }

  @Test
  void v2PopupDtosDoNotExposeCallerIdentity() throws Exception {
    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.V2PopupResponseDto"))
        .doesNotContain("userUuid", "uid");
    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.V2RegionDistrictsResponseDto"))
        .containsExactly("districtList", "region");
  }

  private String[] fieldNames(String className) throws ClassNotFoundException {
    return Arrays.stream(Class.forName(className).getDeclaredFields())
        .map(java.lang.reflect.Field::getName)
        .sorted()
        .toArray(String[]::new);
  }

  private List<String> endpointsOf(String className) throws ClassNotFoundException {
    Class<?> controller = Class.forName(className);
    RequestMapping classMapping =
        AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
    String basePath = pathOf(classMapping);
    List<String> endpoints = new ArrayList<>();

    for (Method method : controller.getDeclaredMethods()) {
      RequestMapping methodMapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (methodMapping == null) {
        continue;
      }
      String path = (basePath + "/" + pathOf(methodMapping)).replaceAll("/{2,}", "/");
      if (path.length() > 1 && path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      for (RequestMethod requestMethod : methodMapping.method()) {
        endpoints.add(requestMethod.name() + " " + path);
      }
    }
    return endpoints;
  }

  private String pathOf(RequestMapping mapping) {
    if (mapping == null) {
      return "";
    }
    String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
    return paths.length == 0 ? "" : paths[0];
  }
}
