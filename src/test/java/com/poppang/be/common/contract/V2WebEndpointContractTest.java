package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.poppang.be.common.response.ApiResponse;
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

class V2WebEndpointContractTest {

  private static final String POPUP_CONTROLLER =
      "com.poppang.be.domain.popup.presentation.v2.V2PopupWebController";
  private static final String RECOMMEND_CONTROLLER =
      "com.poppang.be.domain.recommend.presentation.v2.V2WebRecommendController";

  @Test
  void exposesExactlyTheApprovedSevenPublicGetMappings() throws Exception {
    List<String> endpoints = new ArrayList<>();
    endpoints.addAll(endpointsOf(POPUP_CONTROLLER));
    endpoints.addAll(endpointsOf(RECOMMEND_CONTROLLER));

    assertThat(endpoints)
        .containsExactlyInAnyOrder(
            "GET /api/v2/web/popup/random",
            "GET /api/v2/web/popup/favorite",
            "GET /api/v2/web/popup/in-progress",
            "GET /api/v2/web/popup/upcoming",
            "GET /api/v2/web/popup/search",
            "GET /api/v2/web/popup/{popupUuid}",
            "GET /api/v2/web/recommend");
  }

  @Test
  void publicWebControllersDoNotAcceptPrincipalOrCallerUuid() throws Exception {
    Set<String> parameterTypeNames =
        controllers().stream()
            .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .map(parameter -> parameter.getType().getName())
            .collect(Collectors.toSet());
    Set<String> parameterNames =
        controllers().stream()
            .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .map(Parameter::getName)
            .collect(Collectors.toSet());

    assertThat(parameterTypeNames)
        .doesNotContain(
            "com.poppang.be.common.security.JwtPrincipal",
            "org.springframework.security.core.Authentication");
    assertThat(parameterNames).doesNotContain("userUuid", "uid", "principal", "authentication");
  }

  @Test
  void publicWebResponsesUseApiResponseAndDedicatedDtos() throws Exception {
    for (Class<?> controller : controllers()) {
      for (Method method : controller.getDeclaredMethods()) {
        if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null) {
          assertThat(method.getReturnType()).isEqualTo(ApiResponse.class);
        }
      }
    }

    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.web.V2PopupWebRandomResponseDto"))
        .containsExactlyInAnyOrder("popupUuid", "name", "thumbnailUrl");
    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.web.V2PopupWebFavoriteResponseDto"))
        .containsExactlyInAnyOrder(
            "popupUuid", "name", "thumbnailUrl", "region", "startDate", "endDate");
    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.web.V2PopupWebInProgressResponseDto"))
        .containsExactlyInAnyOrder(
            "popupUuid", "name", "thumbnailUrl", "region", "startDate", "endDate");
    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.web.V2PopupWebUpcomingResponseDto"))
        .containsExactlyInAnyOrder(
            "popupUuid", "name", "thumbnailUrl", "region", "startDate", "endDate", "dDay");
    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.web.V2PopupWebSearchResponseDto"))
        .containsExactlyInAnyOrder(
            "popupUuid", "name", "thumbnailUrl", "region", "startDate", "endDate");
    assertThat(fieldNames("com.poppang.be.domain.popup.dto.v2.web.V2PopupWebDetailResponseDto"))
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
            "instaPostUrl",
            "captionSummary",
            "imageUrlList",
            "recommendList",
            "favoriteCount",
            "viewCount");
    assertThat(fieldNames("com.poppang.be.domain.recommend.dto.v2.V2WebRecommendResponseDto"))
        .containsExactlyInAnyOrder("id", "recommendName");
  }

  @Test
  void forbiddenLegacyShapedV2RecommendWebMappingDoesNotExist() throws Exception {
    Class<?> appRecommendController =
        Class.forName("com.poppang.be.domain.recommend.presentation.v2.V2RecommendController");

    assertThat(endpointsOf(appRecommendController))
        .doesNotContain("GET /api/v2/recommend/web")
        .contains("GET /api/v2/recommend", "GET /api/v2/recommend/featured");
  }

  private List<Class<?>> controllers() throws ClassNotFoundException {
    return List.of(Class.forName(POPUP_CONTROLLER), Class.forName(RECOMMEND_CONTROLLER));
  }

  private Set<String> fieldNames(String className) throws ClassNotFoundException {
    return Arrays.stream(Class.forName(className).getDeclaredFields())
        .map(java.lang.reflect.Field::getName)
        .collect(Collectors.toSet());
  }

  private List<String> endpointsOf(String className) throws ClassNotFoundException {
    return endpointsOf(Class.forName(className));
  }

  private List<String> endpointsOf(Class<?> controller) {
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
