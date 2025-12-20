package com.poppang.be.domain.recommend.entity;

import com.poppang.be.domain.users.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_recommend")
public class UserRecommend {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "users_id", nullable = false)
  private Users user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recommend_id", nullable = false)
  private Recommend recommend;

  public UserRecommend(Users user, Recommend recommend) {
    this.user = user;
    this.recommend = recommend;
  }
}
