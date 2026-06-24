package com.poppang.be.domain.popup.entity;

import com.poppang.be.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

  @Column(name = "open_time", nullable = true)
  private LocalTime openTime;

  @Column(name = "close_time", nullable = true)
  private LocalTime closeTime;

  @Column(name = "address", nullable = true, length = 255)
  private String address;

  @Column(name = "road_address", nullable = false, length = 255)
  private String roadAddress;

  @Column(name = "region", nullable = false, length = 100)
  private String region;

  @Column(name = "insta_post_url", nullable = true, length = 255)
  private String instaPostUrl;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "submitter_user_uuid", nullable = false, length = 36)
  private String submitterUserUuid;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PopupSubmissionStatus status;

  @Builder
  public PopupSubmission(
      String name,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime openTime,
      LocalTime closeTime,
      String address,
      String roadAddress,
      String region,
      String instaPostUrl,
      String description,
      String submitterUserUuid,
      PopupSubmissionStatus status) {
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.address = address;
    this.roadAddress = roadAddress;
    this.region = region;
    this.instaPostUrl = instaPostUrl;
    this.description = description;
    this.submitterUserUuid = submitterUserUuid;
    this.status = status;
  }

  public void updateStatus(PopupSubmissionStatus popupSubmissionStatus) {
    this.status = popupSubmissionStatus;
  }
}
