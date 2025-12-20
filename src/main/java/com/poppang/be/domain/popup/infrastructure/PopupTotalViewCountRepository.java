package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupTotalViewCount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupTotalViewCountRepository extends JpaRepository<PopupTotalViewCount, String> {

  @Modifying
  @Query(
      value =
          """
            INSERT INTO popup_total_view_count (popup_uuid, view_count)
            VALUES (:uuid, :delta)
            ON DUPLICATE KEY UPDATE view_count = view_count + VALUES(view_count)
            """,
      nativeQuery = true)
  int upsertAdd(@Param("uuid") String uuid, @Param("delta") long delta);

  @Query(
      value =
          """
              SELECT popup_uuid AS popupUuid,
                     view_count AS viewCount
              FROM popup_total_view_count
              WHERE popup_uuid IN :uuids
            """,
      nativeQuery = true)
  List<ViewCountProjection> findAllViewCounts(@Param("uuids") List<String> uuids);

  public interface ViewCountProjection {
    String getPopupUuid();

    Long getViewCount();
  }

  @Query(
      value =
          """
                        SELECT view_count
                        FROM popup_total_view_count
                        WHERE popup_uuid = :popupUuid
            """,
      nativeQuery = true)
  Long getViewCountByPopupUuid(String popupUuid);
}
