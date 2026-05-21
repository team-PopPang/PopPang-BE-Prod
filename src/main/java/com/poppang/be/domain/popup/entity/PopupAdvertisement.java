package com.poppang.be.domain.popup.entity;

import com.poppang.be.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "popup_advertisement")
public class PopupAdvertisement extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "popup_id", nullable = false)
  private Long popupId;

  @Enumerated(EnumType.STRING)
  @Column(name = "placement", nullable = false, length = 50)
  private PopupAdvertisementPlacement placement;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "ad_start_at", nullable = false)
  private LocalDateTime adStartAt;

  @Column(name = "ad_end_at", nullable = false)
  private LocalDateTime adEndAt;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "advertiser_name", length = 100)
  private String advertiserName;

  @Column(name = "memo", length = 500)
  private String memo;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;
}
