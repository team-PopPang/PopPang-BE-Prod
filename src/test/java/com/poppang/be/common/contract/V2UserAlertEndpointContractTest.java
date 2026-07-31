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

class V2UserAlertEndpointContractTest {

  @Test
  void v2UserAlertControllerExists() {
    assertThatCode(
            () ->
                Class.forName("com.poppang.be.domain.alert.presentation.v2.V2UserAlertController"))
        .doesNotThrowAnyException();
  }

  @Test
  void v2UserAlertExposesOnlyTheThreeApprovedUserMappings() throws Exception {
    assertThat(endpointsOf("com.poppang.be.domain.alert.presentation.v2.V2UserAlertController"))
        .containsExactlyInAnyOrder(
            "GET /api/v2/user/alert/popups",
            "DELETE /api/v2/user/alert",
            "PATCH /api/v2/user/alert/read")
        .noneMatch(endpoint -> endpoint.startsWith("POST "))
        .noneMatch(endpoint -> endpoint.matches(".*\\{.*[Uu]ser[Uu]uid.*}.*"));
  }

  @Test
  void v2DeleteRequestContainsOnlyThePopupTarget() throws Exception {
    assertThat(fieldNames("com.poppang.be.domain.alert.dto.v2.V2UserAlertDeleteRequestDto"))
        .containsExactly("popupUuid");
  }

  @Test
  void v2ResponseKeepsTheLegacyAlertFields() throws Exception {
    assertThat(fieldNames("com.poppang.be.domain.alert.dto.v2.V2UserAlertResponseDto"))
        .containsExactlyInAnyOrder(
            "popupUuid",
            "name",
            "startDate",
            "endDate",
            "openTime",
            "closeTime",
            "address",
            "roadAddress",
            "region",
            "latitude",
            "longitude",
            "instaPostId",
            "instaPostUrl",
            "captionSummary",
            "imageUrlList",
            "mediaType",
            "recommendList",
            "favoriteCount",
            "viewCount",
            "favorited",
            "read");
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
