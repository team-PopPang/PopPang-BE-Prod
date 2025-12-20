package com.poppang.be.domain.popup.entity;

import com.poppang.be.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "popup")
public class Popup extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uuid", nullable = false, length = 36)
  private String uuid;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "open_time", nullable = true)
  private LocalTime openTime;

  @Column(name = "close_time", nullable = true)
  private LocalTime closeTime;

  @Column(name = "address", nullable = false, length = 255)
  private String address;

  @Column(name = "road_address", nullable = true, length = 255)
  private String roadAddress;

  @Column(name = "region", nullable = false, length = 100)
  private String region;

  @Column(name = "latitude", nullable = true)
  private Double latitude;

  @Column(name = "longitude", nullable = true)
  private Double longitude;

  @Column(name = "geocoding_query", nullable = true)
  private String geocodingQuery;

  @Column(name = "insta_post_id", nullable = false, length = 255, unique = true)
  private String instaPostId;

  @Column(name = "insta_post_url", nullable = false, length = 255)
  private String instaPostUrl;

  @Column(name = "caption_summary", nullable = false, columnDefinition = "TEXT")
  private String captionSummary;

  @Column(name = "caption", nullable = false, columnDefinition = "TEXT")
  private String caption;

  @Enumerated(EnumType.STRING)
  @Column(name = "media_type", nullable = true)
  private MediaType mediaType;

  @Column(name = "is_active", nullable = false)
  private boolean activated;

  @PrePersist
  private void ensureUuid() {
    if (this.uuid == null || this.uuid.isBlank()) {
      this.uuid = UUID.randomUUID().toString();
    }
  }

  @Builder
  public Popup(
      Long id,
      String uuid,
      String name,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime openTime,
      LocalTime closeTime,
      String address,
      String roadAddress,
      String region,
      Double latitude,
      Double longitude,
      String geocodingQuery,
      String instaPostId,
      String instaPostUrl,
      String captionSummary,
      String caption,
      MediaType mediaType,
      boolean activated) {
    this.id = id;
    this.uuid = uuid;
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.address = address;
    this.roadAddress = roadAddress;
    this.region = region;
    this.latitude = latitude;
    this.longitude = longitude;
    this.geocodingQuery = geocodingQuery;
    this.instaPostId = instaPostId;
    this.instaPostUrl = instaPostUrl;
    this.captionSummary = captionSummary;
    this.caption = caption;
    this.mediaType = mediaType;
    this.activated = activated;
  }

  public void deactivate() {
    this.activated = false;
  }
}
