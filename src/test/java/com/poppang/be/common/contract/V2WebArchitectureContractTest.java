package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.poppang.be.domain.popup.presentation.v2.V2PopupWebController;
import com.poppang.be.domain.recommend.presentation.v2.V2WebRecommendController;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class V2WebArchitectureContractTest {

  private static final List<Class<?>> CONTROLLERS =
      List.of(V2PopupWebController.class, V2WebRecommendController.class);

  @Test
  void webControllersExposeOnlyGetMappingsWithoutAuthenticationOrCallerIdentity() {
    for (Class<?> controller : CONTROLLERS) {
      for (Method method : controller.getDeclaredMethods()) {
        RequestMapping mapping =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
          continue;
        }

        assertThat(mapping.method()).containsExactly(RequestMethod.GET);
        assertThat(Arrays.stream(method.getParameters()).map(Parameter::getType))
            .noneMatch(
                type ->
                    type.getName().equals("com.poppang.be.common.security.JwtPrincipal")
                        || type.getName()
                            .equals("org.springframework.security.core.Authentication"));
        assertThat(Arrays.stream(method.getParameters()).map(Parameter::getName))
            .noneMatch(
                name -> Set.of("userUuid", "uid", "principal", "authentication").contains(name));
      }
    }
  }

  @Test
  void webResponseDtosContainNoUserOrAdministratorState() {
    Set<String> forbiddenNames =
        Set.of(
            "userUuid",
            "uid",
            "role",
            "fcmToken",
            "isFavorited",
            "email",
            "nickname",
            "signupStatus");
    List<String> dtoClasses =
        List.of(
            "com.poppang.be.domain.popup.dto.v2.web.V2PopupWebRandomResponseDto",
            "com.poppang.be.domain.popup.dto.v2.web.V2PopupWebFavoriteResponseDto",
            "com.poppang.be.domain.popup.dto.v2.web.V2PopupWebInProgressResponseDto",
            "com.poppang.be.domain.popup.dto.v2.web.V2PopupWebUpcomingResponseDto",
            "com.poppang.be.domain.popup.dto.v2.web.V2PopupWebSearchResponseDto",
            "com.poppang.be.domain.popup.dto.v2.web.V2PopupWebDetailResponseDto",
            "com.poppang.be.domain.recommend.dto.v2.V2WebRecommendResponseDto");

    Set<String> actualNames =
        dtoClasses.stream()
            .map(this::loadClass)
            .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
            .map(java.lang.reflect.Field::getName)
            .collect(Collectors.toSet());

    assertThat(actualNames).doesNotContainAnyElementsOf(forbiddenNames);
  }

  private Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new AssertionError("v2 Web DTO가 없습니다: " + className, exception);
    }
  }
}
