package com.poppang.be.domain.recommend.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "recommend")
public class Recommend {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uuid", nullable = false, length = 36)
  private String uuid;

  @Column(name = "recommend_name", nullable = false, unique = true, length = 100)
  private String recommendName;

  @PrePersist
  private void ensureUuid() {
    if (this.uuid == null || this.uuid.isBlank()) {
      this.uuid = UUID.randomUUID().toString();
    }
  }
}
