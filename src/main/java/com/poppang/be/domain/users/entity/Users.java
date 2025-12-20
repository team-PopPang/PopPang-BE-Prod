package com.poppang.be.domain.users.entity;

import com.poppang.be.common.entity.BaseEntity;
import com.poppang.be.common.enums.Role;
import com.poppang.be.domain.auth.kakao.dto.request.SignupRequestDto;
import com.poppang.be.domain.users.dto.request.ChangeNicknameRequestDto;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class Users extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false, updatable = false)
  private Long id;

  @Column(name = "uid", nullable = true, unique = true, length = 255)
  private String uid;

  @Column(name = "uuid", nullable = false, length = 36)
  private String uuid;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = true, length = 20)
  private Provider provider;

  @Column(name = "email", nullable = true, length = 255)
  private String email;

  @Column(name = "nickname", nullable = true, length = 255, unique = true)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = true, length = 20)
  private Role role;

  @Column(name = "is_alerted", nullable = true)
  private boolean alerted = false;

  @Column(name = "fcm_token", nullable = true, length = 255)
  private String fcmToken;

  @Column(name = "is_deleted", nullable = true)
  private boolean deleted = false;

  @PrePersist
  private void ensureUuid() {
    if (this.uuid == null || this.uuid.isBlank()) {
      this.uuid = UUID.randomUUID().toString();
    }
  }

  @Builder
  public Users(
      Long id,
      String uid,
      String uuid,
      Provider provider,
      String email,
      String nickname,
      Role role,
      boolean alerted,
      String fcmToken,
      boolean deleted) {
    this.id = id;
    this.uid = uid;
    this.uuid = uuid;
    this.provider = provider;
    this.email = email;
    this.nickname = nickname;
    this.role = role;
    this.alerted = alerted;
    this.fcmToken = fcmToken;
    this.deleted = deleted;
  }

  public void completeSignup(SignupRequestDto signupRequestDto) {
    this.email = signupRequestDto.getEmail();
    this.nickname = signupRequestDto.getNickname();
    this.alerted = signupRequestDto.isAlerted();
    this.fcmToken = signupRequestDto.getFcmToken();
  }

  public void changeNickname(ChangeNicknameRequestDto changeNicknameRequestDto) {
    this.nickname = changeNicknameRequestDto.getNickname();
  }

  public void softDelete() {
    this.deleted = true;
  }

  public void restore() {
    this.deleted = false;
  }

  public void updateFcmToken(String fcmToken) {
    this.fcmToken = fcmToken;
  }

  public void updateAlerted(boolean alerted) {
    this.alerted = alerted;
  }
}
