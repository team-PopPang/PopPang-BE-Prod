package com.poppang.be.domain.favorite.entity;

import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.users.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_favorite")
public class UserFavorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "users_id", nullable = false)
  private Users user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "popup_id", nullable = false)
  private Popup popup;

  public UserFavorite(Users user, Popup popup) {
    this.user = user;
    this.popup = popup;
  }
}
