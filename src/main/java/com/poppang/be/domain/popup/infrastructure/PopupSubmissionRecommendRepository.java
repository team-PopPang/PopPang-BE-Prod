package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupSubmissionRecommend;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PopupSubmissionRecommendRepository
    extends JpaRepository<PopupSubmissionRecommend, Long> {

  @Query(
      """
      SELECT psr
      FROM PopupSubmissionRecommend psr
      JOIN FETCH psr.recommend r
      WHERE psr.popupSubmission.id = :popupSubmissionId
      ORDER BY psr.id ASC
      """)
  List<PopupSubmissionRecommend> findAllByPopupSubmissionIdWithRecommend(
      @Param("popupSubmissionId") Long popupSubmissionId);
}
