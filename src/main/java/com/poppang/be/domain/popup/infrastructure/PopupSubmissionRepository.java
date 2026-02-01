package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PopupSubmissionRepository extends JpaRepository<PopupSubmission, Long> {

  List<PopupSubmission> findByStatus(PopupSubmissionStatus status);
}
