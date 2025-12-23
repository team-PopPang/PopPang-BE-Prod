package com.poppang.be.domain.alert.infrastructure;

import com.poppang.be.domain.alert.entity.UserAlert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

  Optional<UserAlert> findByUser_IdAndPopup_Id(Long userId, Long popupId);

  boolean existsByUser_IdAndPopup_Id(Long userId, Long popupId);

  @Query(
      """
                SELECT ua
                FROM UserAlert ua
                JOIN FETCH ua.popup p
                WHERE ua.user.id = :userId
                AND p.activated = true
                ORDER BY ua.alertedAt DESC
            """)
  List<UserAlert> findAllByUserIdOrderByAlertedAtDesc(@Param("userId") Long userId);
}
