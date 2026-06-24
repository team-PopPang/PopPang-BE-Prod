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
@Table(name = "popup_submission_recommend")
public class PopupSubmissionRecommend extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "popup_submission_id", nullable = false)
  private PopupSubmission popupSubmission;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recommend_id", nullable = false)
  private Recommend recommend;

  @Builder
  public PopupSubmissionRecommend(PopupSubmission popupSubmission, Recommend recommend) {
    this.popupSubmission = popupSubmission;
    this.recommend = recommend;
  }
}
