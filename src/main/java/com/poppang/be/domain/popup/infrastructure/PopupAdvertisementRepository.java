package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupAdvertisement;
import com.poppang.be.domain.popup.entity.PopupAdvertisementPlacement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupAdvertisementRepository extends JpaRepository<PopupAdvertisement, Long> {

  @Query(
      """
            SELECT pa
            FROM PopupAdvertisement pa
            WHERE pa.placement = :placement
              AND pa.active = true
              AND pa.deletedAt IS NULL
              AND pa.adStartAt <= :now
              AND pa.adEndAt >= :now
            ORDER BY pa.priority ASC, pa.id DESC
            """)
  List<PopupAdvertisement> findActiveAdvertisements(
      @Param("placement") PopupAdvertisementPlacement placement, @Param("now") LocalDateTime now);
}
