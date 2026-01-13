package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PopupRecommendRepository extends JpaRepository<PopupRecommend, Long> {

  @Query(
      """
        SELECT pr
        FROM PopupRecommend pr
        JOIN FETCH pr.recommend r
        WHERE pr.popup.id IN :popupIdList
""")
  List<PopupRecommend> findAllByPopupIdsWithRecommend(List<Long> popupIdList);

  PopupRecommend findFirstByPopup_Id(Long id);

  @Query(
      """
            SELECT pr.popup
            FROM PopupRecommend pr
            WHERE pr.recommend.id = :recommendId
              AND pr.popup.activated = true
              AND pr.popup.startDate <= CURRENT_DATE
              AND pr.popup.endDate >= CURRENT_DATE
            ORDER BY pr.popup.createdAt DESC
            """)
  List<Popup> findActivePopupsByRecommendId(Long recommendId, Pageable pageable);

  Optional<PopupRecommend> findByPopupId(Long popupId);

  @Query(
      """
                SELECT pr.popup
                FROM PopupRecommend pr
                WHERE pr.recommend.id = :recommendId
                AND pr.popup.activated = true
                AND pr.popup.startDate <= CURRENT_DATE
                AND pr.popup.endDate >= CURRENT_DATE
            """)
  List<Popup> findRelatedActivePopupList(@Param("recommendId") Long recommendId);

  List<PopupRecommend> findAllByPopup_Id(Long popupId);
}
