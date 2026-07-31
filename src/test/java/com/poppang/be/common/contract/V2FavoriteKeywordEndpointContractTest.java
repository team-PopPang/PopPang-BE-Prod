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

class V2FavoriteKeywordEndpointContractTest {

  @Test
  void v2FavoriteAndKeywordControllersExist() {
    assertThatCode(
            () ->
                Class.forName(
                    "com.poppang.be.domain.favorite.presentation.v2.V2UserFavoriteController"))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                Class.forName(
                    "com.poppang.be.domain.keyword.presentation.v2.V2UserAlertKeywordController"))
        .doesNotThrowAnyException();
  }

  @Test
  void v2FavoriteAndKeywordExposeOnlyTheSevenApprovedMappings() throws Exception {
    List<String> actual = new ArrayList<>();
    actual.addAll(
        endpointsOf("com.poppang.be.domain.favorite.presentation.v2.V2UserFavoriteController"));
    actual.addAll(
        endpointsOf("com.poppang.be.domain.keyword.presentation.v2.V2UserAlertKeywordController"));

    assertThat(actual)
        .containsExactlyInAnyOrder(
            "POST /api/v2/favorite",
            "DELETE /api/v2/favorite",
            "GET /api/v2/favorite/count/{popupUuid}",
            "GET /api/v2/favorite/popup",
            "GET /api/v2/alert-keyword",
            "POST /api/v2/alert-keyword",
            "DELETE /api/v2/alert-keyword")
        .noneMatch(endpoint -> endpoint.matches(".*\\{.*[Uu]ser[Uu]uid.*}.*"));
  }

  @Test
  void v2RequestDtosContainOnlyResourceTargets() throws Exception {
    assertThat(fieldNames("com.poppang.be.domain.favorite.dto.v2.V2FavoriteRequestDto"))
        .containsExactly("popupUuid");
    assertThat(fieldNames("com.poppang.be.domain.keyword.dto.v2.V2AlertKeywordRequestDto"))
        .containsExactly("keyword");
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
