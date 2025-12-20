package com.poppang.be.common.mail;

import com.poppang.be.domain.users.entity.Users;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

  @Value("${poppang.mail.from}")
  private String from;

  @Value("${poppang.mail.password}")
  private String password;

  @Value("${poppang.mail.admins}")
  private String admins;

  /** 회원가입 완료 시 관리자에게 알림 메일 보내기 */
  public void sendNewUserSignUpMail(Users user) {
    // 1. 설정이 제대로 되어 있는지 먼저 확인
    if (!isMailConfigured()) {
      log.warn("✉️ 메일 설정이 없어 회원가입 알림 메일을 전송하지 않습니다.");
      return;
    }

    String subject = "[팝팡] 새 사용자가 가입했습니다!";
    String body = buildUserSignUpBody(user);

    try {
      sendMailToAdmins(subject, body);
    } catch (Exception e) {
      // 여기서는 절대로 예외를 위로 던지지 말 것
      // → 회원가입 트랜잭션이 메일 때문에 롤백되면 안 됨
      log.error("❌ 회원가입 알림 메일 전송 실패: {}", e.getMessage(), e);
    }
  }

  /** 실제 메일 전송 로직 */
  private void sendMailToAdmins(String subject, String body) throws MessagingException {
    Session session = createMailSession();

    Message message = new MimeMessage(session);
    message.setFrom(new InternetAddress(from));

    for (String to : getAdminEmailList()) {
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
    }

    message.setSubject(subject);

    // 본문 설정 (텍스트)
    MimeBodyPart textPart = new MimeBodyPart();
    textPart.setText(body, "UTF-8");

    MimeMultipart multipart = new MimeMultipart();
    multipart.addBodyPart(textPart);
    message.setContent(multipart);

    Transport.send(message);
    log.info("✅ 관리자 알림 메일 전송 성공. subject={}, admins={}", subject, getAdminEmailList());
  }

  /** JavaMail Session 생성 */
  private Session createMailSession() {
    Properties props = new Properties();
    // Gmail SMTP 기본 설정 (SSL 465)
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.port", "465");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.ssl.enable", "true");

    return Session.getInstance(
        props,
        new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(from, password);
          }
        });
  }

  /** 메일 설정이 제대로 되어 있는지 체크 */
  private boolean isMailConfigured() {
    if (isBlank(from) || isBlank(password) || admins == null || admins.isEmpty()) {
      log.warn("메일 설정값이 비어 있습니다. from={}, admins={}", from, admins);
      return false;
    }
    return true;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  /** admins 설정값을 List<String>으로 반환 - 공백 제거 - 빈 문자열 제거 */
  private List<String> getAdminEmailList() {
    return Arrays.stream(admins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /** 회원가입 알림 메일 본문 생성 */
  private String buildUserSignUpBody(Users user) {
    StringBuilder sb = new StringBuilder();
    sb.append("신규 유저가 가입했습니다.\n\n");
    sb.append("닉네임: ").append(user.getNickname()).append("\n");
    sb.append("이메일: ").append(user.getEmail()).append("\n");
    sb.append("플랫폼: ").append(user.getProvider()).append("\n");
    sb.append("가입 날짜: ").append(user.getCreatedAt()).append("\n");

    return sb.toString();
  }
}
