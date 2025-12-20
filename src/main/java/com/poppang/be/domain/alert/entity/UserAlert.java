package com.poppang.be.domain.alert.entity;

import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.users.entity.Users;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_alert")
public class UserAlert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "users_id", nullable = false)
  private Users user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "popup_id", nullable = false)
  private Popup popup;

  @Column(name = "alerted_at", nullable = false)
  private LocalDateTime alertedAt;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Builder
  public UserAlert(Users user, Popup popup, LocalDateTime alertedAt, LocalDateTime readAt) {
    this.user = user;
    this.popup = popup;
    this.alertedAt = alertedAt;
    this.readAt = readAt;
  }

  public void markAsRead() {
    this.readAt = LocalDateTime.now();
  }
}
