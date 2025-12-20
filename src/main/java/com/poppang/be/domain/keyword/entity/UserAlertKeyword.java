package com.poppang.be.domain.keyword.entity;

import com.poppang.be.domain.users.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_alert_keyword")
public class UserAlertKeyword {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "users_id", nullable = false)
  private Users user;

  @Column(name = "alert_keyword", nullable = false, length = 100)
  private String alertKeyword;

  @Builder
  public UserAlertKeyword(Users user, String alertKeyword) {
    this.user = user;
    this.alertKeyword = alertKeyword;
  }

  public static UserAlertKeyword from(Users users, String alertKeyword) {
    return UserAlertKeyword.builder().user(users).alertKeyword(alertKeyword).build();
  }
}
