package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

class V2UserPopupScrollEndpointContractTest {

  private static final String CONTROLLER =
      "com.poppang.be.domain.popup.presentation.v2.V2UserPopupController";
  private static final String RESPONSE_DTO =
      "com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto";
  private static final String ITEM_DTO =
      "com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollItemResponseDto";

  @Test
  void exposesOnlyTheApprovedScrollGetWithOptionalCursor() throws Exception {
    Method method = scrollMethod();
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.method()).containsExactly(RequestMethod.GET);
    assertThat(mapping.path().length > 0 ? mapping.path() : mapping.value())
        .containsExactly("/scroll");

    Parameter cursor =
        Arrays.stream(method.getParameters())
            .filter(parameter -> parameter.isAnnotationPresent(RequestParam.class))
            .findFirst()
            .orElseThrow();
    RequestParam requestParam = cursor.getAnnotation(RequestParam.class);
    assertThat(requestParam.name()).isEqualTo("cursor");
    assertThat(requestParam.required()).isFalse();
    assertThat(cursor.getType()).isEqualTo(Long.class);
  }

  @Test
  void scrollContractDoesNotExposeCallerUuidAndKeepsLegacyResponseFields() throws Exception {
    Set<String> requestParameterNames =
        Arrays.stream(scrollMethod().getParameters())
            .map(parameter -> parameter.getAnnotation(RequestParam.class))
            .filter(java.util.Objects::nonNull)
            .map(
                requestParam ->
                    requestParam.name().isBlank() ? requestParam.value() : requestParam.name())
            .collect(Collectors.toSet());
    Set<String> responseFields = fieldNames(RESPONSE_DTO);
    Set<String> itemFields = fieldNames(ITEM_DTO);

    assertThat(requestParameterNames).containsExactly("cursor").doesNotContain("userUuid", "uid");
    assertThat(responseFields).containsExactlyInAnyOrder("items", "nextCursor", "hasNext");
    assertThat(itemFields)
        .containsExactlyInAnyOrder(
            "popupUuid", "thumbnailUrl", "region", "name", "startDate", "endDate", "favorited")
        .doesNotContain("userUuid", "uid");
  }

  private Method scrollMethod() throws ClassNotFoundException {
    return Arrays.stream(Class.forName(CONTROLLER).getDeclaredMethods())
        .filter(method -> method.getName().equals("getScrollPopupList"))
        .findFirst()
        .orElseThrow();
  }

  private Set<String> fieldNames(String className) throws ClassNotFoundException {
    return Arrays.stream(Class.forName(className).getDeclaredFields())
        .map(java.lang.reflect.Field::getName)
        .collect(Collectors.toSet());
  }
}
