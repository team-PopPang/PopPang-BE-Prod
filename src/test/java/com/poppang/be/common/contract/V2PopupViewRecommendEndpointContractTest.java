package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class V2PopupViewRecommendEndpointContractTest {

  private static final String POPUP_VIEW_CONTROLLER =
      "com.poppang.be.domain.popup.presentation.v2.V2PopupTotalViewController";
  private static final String RECOMMEND_CONTROLLER =
      "com.poppang.be.domain.recommend.presentation.v2.V2RecommendController";

  @Test
  void v2PopupTotalViewControllerExposesExactlyTheThreeApprovedMappings() throws Exception {
    assertThat(endpointsOf(POPUP_VIEW_CONTROLLER))
        .containsExactlyInAnyOrder(
            "POST /api/v2/popup/{popupUuid}/view",
            "GET /api/v2/popup/{popupUuid}/total-view-count",
            "GET /api/v2/popup/{popupUuid}/view-count")
        .noneMatch(endpoint -> endpoint.toLowerCase().contains("useruuid"));
  }

  @Test
  void v2RecommendControllerExposesExactlyTheTwoApprovedAppMappings() throws Exception {
    assertThat(endpointsOf(RECOMMEND_CONTROLLER))
        .containsExactlyInAnyOrder("GET /api/v2/recommend", "GET /api/v2/recommend/featured")
        .noneMatch(endpoint -> endpoint.toLowerCase().contains("useruuid"))
        .noneMatch(endpoint -> endpoint.toLowerCase().contains("web"));
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
