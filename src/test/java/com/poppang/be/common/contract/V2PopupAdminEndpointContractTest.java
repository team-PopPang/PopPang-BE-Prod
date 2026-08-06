package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class V2PopupAdminEndpointContractTest {

  private static final String CONTROLLER =
      "com.poppang.be.domain.popup.presentation.v2.V2PopupAdminController";
  private static final String DTO_PACKAGE = "com.poppang.be.domain.popup.dto.v2.admin.";

  @Test
  void exposesExactlyTheFiveApprovedAdminMappings() throws Exception {
    assertThat(endpointsOf(CONTROLLER))
        .containsExactlyInAnyOrder(
            "PATCH /api/v2/admin/popup/{popupUuid}/deactivate",
            "GET /api/v2/admin/popup-submissions",
            "GET /api/v2/admin/popup-submissions/{popupSubmissionId}",
            "PUT /api/v2/admin/popup-submissions/{popupSubmissionId}",
            "PATCH /api/v2/admin/popup-submissions/{submissionId}/status");
  }

  @Test
  void everyAdminMappingRequiresAccessTokenAndCurrentAdminRole() throws Exception {
    Class<?> controller = Class.forName(CONTROLLER);
    PreAuthorize authorization =
        AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);

    assertThat(authorization).isNotNull();
    assertThat(authorization.value())
        .contains("hasAuthority('TOKEN_ACCESS')")
        .contains("hasRole('ADMIN')");
  }

  @Test
  void adminMappingsDoNotExposeCallerUuidAndUseOnlyDedicatedV2Dtos() throws Exception {
    Class<?> controller = Class.forName(CONTROLLER);
    Set<String> parameterNames =
        Arrays.stream(controller.getDeclaredMethods())
            .filter(this::isEndpoint)
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .map(Parameter::getName)
            .collect(Collectors.toSet());
    Set<String> dtoTypeNames =
        Arrays.stream(controller.getDeclaredMethods())
            .filter(this::isEndpoint)
            .flatMap(method -> Arrays.stream(method.getParameterTypes()))
            .map(Class::getName)
            .filter(typeName -> typeName.contains(".dto."))
            .collect(Collectors.toSet());

    assertThat(parameterNames).doesNotContain("uuid", "adminUuid", "userUuid", "uid");
    assertThat(dtoTypeNames).allMatch(typeName -> typeName.startsWith(DTO_PACKAGE));
  }

  @Test
  void approvalMappingKeepsLegacyMultipartPartsWithoutCallerUuid() throws Exception {
    Method method =
        Arrays.stream(Class.forName(CONTROLLER).getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("updatePopupSubmission"))
            .findFirst()
            .orElseThrow();
    RequestMapping mapping =
        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(mapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
    assertThat(Arrays.stream(method.getParameters()).map(Parameter::getName))
        .contains("popupSubmissionId", "request", "images")
        .doesNotContain("uuid", "adminUuid", "userUuid", "uid");
  }

  @Test
  void dedicatedV2DtosPreserveLegacyFieldsWithoutCallerIdentity() throws Exception {
    assertThat(fieldNames(DTO_PACKAGE + "V2PopupSubmissionAdminUpdateRequestDto"))
        .containsExactlyInAnyOrder(
            "status",
            "name",
            "startDate",
            "endDate",
            "roadAddress",
            "region",
            "address",
            "openTime",
            "closeTime",
            "latitude",
            "longitude",
            "captionSummary",
            "caption",
            "mediaType",
            "instaPostUrl",
            "instaPostId",
            "geocodingQuery",
            "imageList",
            "recommendIdList")
        .doesNotContain("uuid", "adminUuid", "userUuid", "uid");
    assertThat(fieldNames(DTO_PACKAGE + "V2PopupSubmissionAdminImageRequestDto"))
        .containsExactlyInAnyOrder("sourceType", "imageUrl", "fileIndex", "sortOrder");
    assertThat(fieldNames(DTO_PACKAGE + "V2PopupSubmissionStatusUpdateRequestDto"))
        .containsExactly("popupSubmissionStatus");
    assertThat(fieldNames(DTO_PACKAGE + "V2PopupSubmissionAdminListResponseDto"))
        .containsExactlyInAnyOrder(
            "popupSubmissionId",
            "name",
            "roadAddress",
            "region",
            "submitterUserUuid",
            "submitterNickname",
            "submittedAt",
            "status");
    assertThat(fieldNames(DTO_PACKAGE + "V2PopupSubmissionAdminDetailResponseDto"))
        .containsExactlyInAnyOrder(
            "popupSubmissionId",
            "name",
            "startDate",
            "endDate",
            "roadAddress",
            "region",
            "description",
            "recommendIdList",
            "recommendList",
            "imageList",
            "address",
            "openTime",
            "closeTime",
            "instaPostUrl",
            "status");
    assertThat(fieldNames(DTO_PACKAGE + "V2PopupSubmissionAdminUpdateResponseDto"))
        .containsExactly("popupUuid");
  }

  private boolean isEndpoint(Method method) {
    return AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null;
  }

  private Set<String> fieldNames(String className) throws ClassNotFoundException {
    return Arrays.stream(Class.forName(className).getDeclaredFields())
        .map(java.lang.reflect.Field::getName)
        .collect(Collectors.toSet());
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
