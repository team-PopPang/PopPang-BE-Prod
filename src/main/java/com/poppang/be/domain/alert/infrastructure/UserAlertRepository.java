package com.poppang.be.domain.alert.infrastructure;

import com.poppang.be.domain.alert.entity.UserAlert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

  Optional<UserAlert> findByUser_IdAndPopup_Id(Long userId, Long popupId);

  boolean existsByUser_IdAndPopup_Id(Long userId, Long popupId);

  List<UserAlert> findAllByUser_IdOrderByAlertedAtDesc(Long userId);
}
