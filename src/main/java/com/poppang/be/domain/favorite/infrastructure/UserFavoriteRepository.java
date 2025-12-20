package com.poppang.be.domain.favorite.infrastructure;

import com.poppang.be.domain.favorite.entity.UserFavorite;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.users.entity.Users;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

  boolean existsByUserAndPopup(Users user, Popup popup);

  Optional<UserFavorite> findByUserUuidAndPopupUuid(String userUuid, String popupUuid);

  long countByPopupUuid(String popupUuid);

  List<UserFavorite> findAllByUserUuid(String userUuid);

  @Query(
      """
              SELECT uf.popup.id AS popupId, COUNT(uf.id) AS cnt
              FROM UserFavorite uf
              WHERE uf.popup.id IN :popupIds
              GROUP BY uf.popup.id
            """)
  List<FavoriteCountRow> countAllByPopupIds(@Param("popupIds") List<Long> popupIds);

  interface FavoriteCountRow {

    Long getPopupId();

    Long getCnt();
  }

  boolean existsByUser_UuidAndPopup_Uuid(String userUuid, String popupUuid);
}
