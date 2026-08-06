package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupSubmissionRepository extends JpaRepository<PopupSubmission, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select popupSubmission from PopupSubmission popupSubmission where popupSubmission.id = :id")
  Optional<PopupSubmission> findByIdForUpdate(@Param("id") Long id);

  List<PopupSubmission> findByStatus(PopupSubmissionStatus status);

  List<PopupSubmission> findByEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(LocalDate endDate);

  List<PopupSubmission> findByStatusAndEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(
      PopupSubmissionStatus status, LocalDate endDate);
}
