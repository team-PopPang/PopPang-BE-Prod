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
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class V2InternalWorkerEndpointContractTest {

  private static final String POPUP_CONTROLLER =
      "com.poppang.be.domain.popup.presentation.v2.V2InternalPopupController";
  private static final String USERS_CONTROLLER =
      "com.poppang.be.domain.users.presentation.v2.V2InternalUsersController";
  private static final String ALERT_CONTROLLER =
      "com.poppang.be.domain.alert.presentation.v2.V2InternalUserAlertController";

  @Test
  void exposesExactlyTheFiveApprovedInternalWorkerMappings() throws Exception {
    List<String> endpoints = new ArrayList<>();
    endpoints.addAll(endpointsOf(POPUP_CONTROLLER));
    endpoints.addAll(endpointsOf(USERS_CONTROLLER));
    endpoints.addAll(endpointsOf(ALERT_CONTROLLER));

    assertThat(endpoints)
        .containsExactlyInAnyOrder(
            "POST /api/v2/internal/popup",
            "PUT /api/v2/internal/popup/{popupUuid}/images",
            "GET /api/v2/internal/user/with-alert-keyword/a",
            "GET /api/v2/internal/user/with-alert-keyword/b",
            "POST /api/v2/internal/users/{userUuid}/alert");
  }

  @Test
  void everyInternalControllerRequiresServiceWorkerAuthority() throws Exception {
    for (String controllerName : List.of(POPUP_CONTROLLER, USERS_CONTROLLER, ALERT_CONTROLLER)) {
      PreAuthorize authorization =
          AnnotatedElementUtils.findMergedAnnotation(
              Class.forName(controllerName), PreAuthorize.class);

      assertThat(authorization).isNotNull();
      assertThat(authorization.value()).contains("hasAuthority('SERVICE_WORKER')");
    }
  }

  @Test
  void internalContractsUseDedicatedV2DtosAndNeverExposeLongUserId() throws Exception {
    assertThat(
            fieldNames(
                "com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordResponseDto"))
        .containsExactlyInAnyOrder("userUuid", "nickname", "fcmToken", "keyword")
        .doesNotContain("userId");
    assertThat(
            fieldNames(
                "com.poppang.be.domain.users.dto.v2.response.V2WorkerUserKeywordGroupResponseDto"))
        .containsExactlyInAnyOrder("userUuid", "nickname", "fcmToken", "keywordList")
        .doesNotContain("userId");

    Set<String> parameterNames =
        List.of(POPUP_CONTROLLER, USERS_CONTROLLER, ALERT_CONTROLLER).stream()
            .flatMap(
                controllerName -> {
                  try {
                    return Arrays.stream(Class.forName(controllerName).getDeclaredMethods());
                  } catch (ClassNotFoundException exception) {
                    throw new IllegalStateException(exception);
                  }
                })
            .filter(this::isEndpoint)
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .map(Parameter::getName)
            .collect(Collectors.toSet());

    assertThat(parameterNames).doesNotContain("userId", "callerUuid", "workerUuid");
  }

  @Test
  void internalControllersDependOnlyOnDedicatedV2Services() throws Exception {
    assertThat(fieldTypeNames(POPUP_CONTROLLER))
        .containsExactly("com.poppang.be.domain.popup.application.V2InternalPopupService");
    assertThat(fieldTypeNames(USERS_CONTROLLER))
        .containsExactly("com.poppang.be.domain.users.application.V2InternalUsersService");
    assertThat(fieldTypeNames(ALERT_CONTROLLER))
        .containsExactly("com.poppang.be.domain.alert.application.V2InternalUserAlertService");
  }

  @Test
  void reusedLegacyPollingQueriesKeepActiveAlertedFilteringAndOrdering() throws Exception {
    Class<?> repository =
        Class.forName("com.poppang.be.domain.users.infrastructure.UsersRepository");
    String pollingA = queryOf(repository, "findUserWithAlertKeywordList");
    String pollingB = queryOf(repository, "findUserWithAlertKeywordListB");

    assertThat(pollingA)
        .contains("u.is_deleted = 0", "u.is_alerted = 1", "order by u.id, k.alert_keyword")
        .doesNotContain("signup_status");
    assertThat(pollingB)
        .contains(
            "u.is_deleted = 0",
            "u.is_alerted = 1",
            "group_concat(distinct k.alert_keyword order by k.alert_keyword separator ',')",
            "group by u.id, u.nickname, u.fcm_token",
            "order by u.id")
        .doesNotContain("signup_status");
  }

  private boolean isEndpoint(Method method) {
    return AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null;
  }

  private Set<String> fieldNames(String className) throws ClassNotFoundException {
    return Arrays.stream(Class.forName(className).getDeclaredFields())
        .map(java.lang.reflect.Field::getName)
        .collect(Collectors.toSet());
  }

  private List<String> fieldTypeNames(String className) throws ClassNotFoundException {
    return Arrays.stream(Class.forName(className).getDeclaredFields())
        .map(field -> field.getType().getName())
        .toList();
  }

  private String queryOf(Class<?> repository, String methodName) {
    Method method =
        Arrays.stream(repository.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    Query query = method.getAnnotation(Query.class);
    assertThat(query).isNotNull();
    return query.value().replaceAll("\\s+", " ").trim().toLowerCase();
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
