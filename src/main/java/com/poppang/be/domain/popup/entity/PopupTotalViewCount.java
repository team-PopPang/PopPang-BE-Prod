package com.poppang.be.domain.popup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "popup_total_view_count")
public class PopupTotalViewCount {

  @Id
  @Column(name = "popup_uuid", length = 36)
  private String popupUuid;

  @Column(name = "view_count", nullable = false)
  private long viewCount;
}
