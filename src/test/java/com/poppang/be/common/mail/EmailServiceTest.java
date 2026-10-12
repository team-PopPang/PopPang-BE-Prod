package com.poppang.be.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.event.UserSignupCompletedEvent;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMultipart;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class EmailServiceTest {

  private final AnnotationConfigApplicationContext context =
      new AnnotationConfigApplicationContext();
  private final TransactionTemplate transaction =
      new TransactionTemplate(new TestTransactionManager());

  @BeforeEach
  void setUp() {
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "mail-test",
                Map.of(
                    "poppang.mail.from", "sender@example.com",
                    "poppang.mail.password", "test-password",
                    "poppang.mail.admins",
                        "devsong@example.com, poppang@example.com, index@example.com")));
    context.register(EmailService.class, TransactionalEventListenerFactory.class);
    context.refresh();
  }

  @AfterEach
  void tearDown() {
    context.close();
  }

  @Test
  void signupNotificationIsSentToAllConfiguredAdminsOnlyAfterCommit() throws Exception {
    try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
      transaction.executeWithoutResult(
          status -> {
            context.publishEvent(new UserSignupCompletedEvent(user()));
            transport.verifyNoInteractions();
          });

      Message message = sentMessage(transport);
      assertThat(message.getRecipients(Message.RecipientType.TO))
          .extracting(Object::toString)
          .containsExactly("devsong@example.com", "poppang@example.com", "index@example.com");
      assertThat(message.getSubject()).isEqualTo("[팝팡] 새 사용자가 가입했습니다!");
      MimeMultipart body = (MimeMultipart) message.getContent();
      assertThat(body.getBodyPart(0).getContent().toString())
          .contains("닉네임: 신규회원", "이메일: user@example.com", "플랫폼: GOOGLE", "가입 날짜: 2026-10-12T10:00");
    }
  }

  @Test
  void rolledBackSignupDoesNotSendNotification() {
    try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
      transaction.executeWithoutResult(
          status -> {
            context.publishEvent(new UserSignupCompletedEvent(user()));
            status.setRollbackOnly();
          });

      transport.verifyNoInteractions();
    }
  }

  @Test
  void smtpFailureDoesNotFailCommittedSignup() {
    try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
      transport
          .when(() -> Transport.send(any(Message.class)))
          .thenThrow(new MessagingException("SMTP unavailable"));

      assertThatCode(
              () ->
                  transaction.executeWithoutResult(
                      status -> context.publishEvent(new UserSignupCompletedEvent(user()))))
          .doesNotThrowAnyException();
      transport.verify(() -> Transport.send(any(Message.class)));
    }
  }

  @Test
  void legacyDirectSignupNotificationStillSendsWithoutTransaction() throws Exception {
    try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
      context.getBean(EmailService.class).sendNewUserSignUpMail(user());

      assertThat(sentMessage(transport).getRecipients(Message.RecipientType.TO)).hasSize(3);
    }
  }

  private Message sentMessage(MockedStatic<Transport> transport) {
    ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
    transport.verify(() -> Transport.send(message.capture()));
    return message.getValue();
  }

  private Users user() {
    Users user = Users.builder().provider(Provider.GOOGLE).email("user@example.com").build();
    user.completeSignup("신규회원", true, "fcm-token");
    ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 10, 12, 10, 0));
    return user;
  }

  private static class TestTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
