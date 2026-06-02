package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupCountBoost;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupCountBoostRepository extends JpaRepository<PopupCountBoost, Long> {

  List<PopupCountBoost> findAllByPopupIdIn(List<Long> popupIds);

  @Query(
      value =
          """
            SELECT
                popup_id AS popupId,
                view_count_boost AS viewCountBoost,
                favorite_count_boost AS favoriteCountBoost
            FROM popup_count_boost
            WHERE popup_id IN :popupIds
            """,
      nativeQuery = true)
  List<PopupCountBoostValueRow> findAllBoostValues(@Param("popupIds") List<Long> popupIds);

  @Query(
      value =
          """
            SELECT pcb.view_count_boost
            FROM popup_count_boost pcb
            JOIN popup p
              ON p.id = pcb.popup_id
            WHERE p.uuid = :popupUuid
            """,
      nativeQuery = true)
  Long getViewCountBoostByPopupUuid(@Param("popupUuid") String popupUuid);

  @Query(
      value =
          """
            SELECT pcb.favorite_count_boost
            FROM popup_count_boost pcb
            JOIN popup p
              ON p.id = pcb.popup_id
            WHERE p.uuid = :popupUuid
            """,
      nativeQuery = true)
  Long getFavoriteCountBoostByPopupUuid(@Param("popupUuid") String popupUuid);

  interface PopupCountBoostValueRow {

    Long getPopupId();

    Long getViewCountBoost();

    Long getFavoriteCountBoost();
  }
}
