package com.poppang.be.domain.users.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UsersTest {

  @Test
  void newUserStartsPendingAndHasAnExplicitCompletionTransition() {
    Users user = Users.builder().uid("social-user").provider(Provider.KAKAO).build();

    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.PENDING);

    user.completeSignup();

    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);

    user.startSignup();

    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
  }

  @Test
  void legacySignupDtoMethodCompletesSignupWhileUpdatingProfile() {
    Users user = Users.builder().uid("social-user").provider(Provider.KAKAO).build();

    SignupRequestDto request = new SignupRequestDto();
    ReflectionTestUtils.setField(request, "email", "verified@example.com");
    ReflectionTestUtils.setField(request, "nickname", "팝팡");
    ReflectionTestUtils.setField(request, "alerted", true);
    ReflectionTestUtils.setField(request, "fcmToken", "fcm-token");

    user.completeSignup(request);

    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
    assertThat(user.getEmail()).isEqualTo("verified@example.com");
    assertThat(user.getNickname()).isEqualTo("팝팡");
    assertThat(user.isAlerted()).isTrue();
    assertThat(user.getFcmToken()).isEqualTo("fcm-token");
  }

  @Test
  void v2SignupCompletesProfileWithoutDependingOnTheLegacyRequestDto() throws Exception {
    Users user = Users.builder().uid("social-user").provider(Provider.KAKAO).build();
    Method completeSignup =
        Users.class.getMethod("completeSignup", String.class, boolean.class, String.class);

    completeSignup.invoke(user, "팝팡-v2", true, "v2-fcm-token");

    assertThat(user.getNickname()).isEqualTo("팝팡-v2");
    assertThat(user.isAlerted()).isTrue();
    assertThat(user.getFcmToken()).isEqualTo("v2-fcm-token");
    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.COMPLETED);
  }

  @Test
  void verifiedEmailUpdateIgnoresMissingValuesAndAcceptsVerifiedValues() throws Exception {
    Users user = Users.builder().email("existing@example.com").build();
    Method updateVerifiedEmail = Users.class.getMethod("updateVerifiedEmail", String.class);

    updateVerifiedEmail.invoke(user, new Object[] {null});
    updateVerifiedEmail.invoke(user, " ");
    assertThat(user.getEmail()).isEqualTo("existing@example.com");

    updateVerifiedEmail.invoke(user, "verified@example.com");
    assertThat(user.getEmail()).isEqualTo("verified@example.com");
  }

  @Test
  void startSignupRepairsNullableExpandRowWithoutDowngradingCompletedUsers() {
    Users user = Users.builder().build();
    ReflectionTestUtils.setField(user, "signupStatus", null);

    user.startSignup();

    assertThat(user.getSignupStatus()).isEqualTo(SignupStatus.PENDING);
  }

  @Test
  void softDeleteKeepsLegacyBehavior() {
    Users user = Users.builder().build();

    user.softDelete();

    assertThat(user.isDeleted()).isTrue();
  }

  @Test
  void schemaExpansionStaysNullableAndUniqueConstraintsBelongToReviewedSql() throws Exception {
    Field signupStatus = Users.class.getDeclaredField("signupStatus");
    Field uid = Users.class.getDeclaredField("uid");

    assertThat(signupStatus.getAnnotation(Column.class).nullable()).isTrue();
    assertThat(signupStatus.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
    assertThat(uid.getAnnotation(Column.class).unique()).isFalse();
  }
}
