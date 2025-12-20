package com.poppang.be.domain.popup.entity;

import com.poppang.be.common.entity.BaseEntity;
import com.poppang.be.domain.recommend.entity.Recommend;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "popup_recommend")
public class PopupRecommend extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "popup_id", nullable = false)
  private Popup popup;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recommend_id", nullable = false)
  private Recommend recommend;

  @Builder
  public PopupRecommend(Popup popup, Recommend recommend) {
    this.popup = popup;
    this.recommend = recommend;
  }
}
