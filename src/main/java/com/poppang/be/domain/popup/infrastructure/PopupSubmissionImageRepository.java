package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupSubmissionImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PopupSubmissionImageRepository extends JpaRepository<PopupSubmissionImage, Long> {

  List<PopupSubmissionImage> findAllByPopupSubmission_IdOrderBySortOrderAscIdAsc(
      Long popupSubmissionId);
}
