package com.poppang.be.domain.popup.entity;

import com.poppang.be.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "popup_count_boost")
public class PopupCountBoost extends BaseEntity implements Persistable<Long> {

  @Id
  @Column(name = "popup_id")
  private Long popupId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "popup_id", nullable = false)
  private Popup popup;

  @Column(name = "view_count_boost", nullable = false)
  private long viewCountBoost;

  @Column(name = "favorite_count_boost", nullable = false)
  private long favoriteCountBoost;

  @Column(name = "last_boosted_date")
  private LocalDate lastBoostedDate;

  public PopupCountBoost(Popup popup) {
    this.popup = popup;
    this.popupId = popup.getId();
  }

  @Override
  @Transient
  public Long getId() {
    return popupId;
  }

  @Override
  @Transient
  public boolean isNew() {
    return getCreatedAt() == null;
  }

  public boolean wasBoostedOn(LocalDate date) {
    return date != null && date.equals(lastBoostedDate);
  }

  public void addBoost(long viewCountDelta, long favoriteCountDelta, LocalDate boostedDate) {
    this.viewCountBoost += viewCountDelta;
    this.favoriteCountBoost += favoriteCountDelta;
    this.lastBoostedDate = boostedDate;
  }
}
