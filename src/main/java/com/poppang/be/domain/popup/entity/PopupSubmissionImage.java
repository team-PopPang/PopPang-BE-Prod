package com.poppang.be.domain.popup.entity;

import com.poppang.be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "popup_submission_image")
public class PopupSubmissionImage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "popup_submission_id", nullable = false)
  private PopupSubmission popupSubmission;

  @Column(name = "image_url", nullable = false, length = 1000)
  private String imageUrl;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  public PopupSubmissionImage(
      Long id, PopupSubmission popupSubmission, String imageUrl, int sortOrder) {
    this.id = id;
    this.popupSubmission = popupSubmission;
    this.imageUrl = imageUrl;
    this.sortOrder = sortOrder;
  }
}
