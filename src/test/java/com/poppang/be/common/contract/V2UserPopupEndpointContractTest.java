package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class V2UserPopupEndpointContractTest {

  private static final String CONTROLLER =
      "com.poppang.be.domain.popup.presentation.v2.V2UserPopupController";
  private static final String RESPONSE_DTO =
      "com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto";

  @Test
  void v2UserPopupControllerExists() {
    assertThatCode(() -> Class.forName(CONTROLLER)).doesNotThrowAnyException();
  }

  @Test
  void v2UserPopupControllerKeepsExactlyTheTwelveApprovedMappings() throws Exception {
    assertThat(endpointsOf(CONTROLLER))
        .containsExactlyInAnyOrderElementsOf(
            Set.of(
                "GET /api/v2/user/popups",
                "GET /api/v2/user/popups/{popupUuid}",
                "GET /api/v2/user/popups/upcoming",
                "GET /api/v2/user/popups/search",
                "GET /api/v2/user/popups/inProgress",
                "GET /api/v2/user/popups/random",
                "GET /api/v2/user/popups/scroll",
                "GET /api/v2/user/popups/filtered/home",
                "GET /api/v2/user/popups/filtered/map",
                "GET /api/v2/user/popups/recommend",
                "GET /api/v2/user/popups/{popupUuid}/related",
                "GET /api/v2/user/popups/recommendations/{recommendId}"));
  }

  @Test
  void v2UserPopupControllerAndDtoDoNotExposeCallerUuid() throws Exception {
    Class<?> controller = Class.forName(CONTROLLER);
    Set<String> parameterNames =
        Arrays.stream(controller.getDeclaredMethods())
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .map(Parameter::getName)
            .collect(Collectors.toSet());
    Set<String> fieldNames =
        Arrays.stream(Class.forName(RESPONSE_DTO).getDeclaredFields())
            .map(java.lang.reflect.Field::getName)
            .collect(Collectors.toSet());

    assertThat(parameterNames).doesNotContain("userUuid", "uid");
    assertThat(fieldNames).doesNotContain("userUuid", "uid").contains("favorited");
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
