package com.poppang.be.domain.popup.entity;

import com.poppang.be.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "popup_submission")
public class PopupSubmission extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "submitter_user_id")
  private Long submitterUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PopupSubmissionStatus status;

  @Builder
  public PopupSubmission(
      String name,
      LocalDate startDate,
      LocalDate endDate,
      String address,
      String description,
      Long submitterUserId,
      PopupSubmissionStatus status) {
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.address = address;
    this.description = description;
    this.submitterUserId = submitterUserId;
    this.status = status;
  }

  public void updateStatus(PopupSubmissionStatus popupSubmissionStatus) {
    this.status = popupSubmissionStatus;
  }
}
