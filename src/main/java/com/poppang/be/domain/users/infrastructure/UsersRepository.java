package com.poppang.be.domain.users.infrastructure;

import com.poppang.be.domain.users.entity.Users;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

  Optional<Users> findByUid(String uid);

  boolean existsByNickname(String nickname);

  Optional<Users> findByUuidAndDeletedFalse(String uuid);

  Optional<Users> findByUuid(String userUuid);

  @Query(
      value =
          """
    SELECT
        u.id AS userId,
        u.nickname AS nickname,
        u.fcm_token AS fcmToken,
        k.alert_keyword AS keyword
    FROM users u
    JOIN user_alert_keyword k ON u.id = k.users_id
    WHERE u.is_deleted = 0
    AND u.is_alerted = 1
    ORDER BY u.id, k.alert_keyword
    """,
      nativeQuery = true)
  List<UserWithKeywordProjection> findUserWithAlertKeywordList();

  public interface UserWithKeywordProjection {
    Long getUserId();

    String getNickname();

    String getFcmToken();

    String getKeyword();
  }

  @Query(
      value =
          """
        SELECT
            u.id AS userId,
            u.nickname AS nickname,
            u.fcm_token AS fcmToken,
            GROUP_CONCAT(DISTINCT k.alert_keyword ORDER BY k.alert_keyword SEPARATOR ',') AS keywordList
        FROM users u
        JOIN user_alert_keyword k
            ON u.id = k.users_id
        WHERE u.is_deleted = 0
        AND u.is_alerted = 1
        GROUP BY u.id, u.nickname, u.fcm_token
        ORDER BY u.id
        """,
      nativeQuery = true)
  List<UserWithKeywordProjectionB> findUserWithAlertKeywordListB();

  public interface UserWithKeywordProjectionB {
    Long getUserId();

    String getNickname();

    String getFcmToken();

    String getKeywordList();
  }
}
