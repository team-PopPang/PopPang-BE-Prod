package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.PopupImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PopupImageRepository extends JpaRepository<PopupImage, Long> {

  void deleteByPopup_Id(Long popupId);

  List<PopupImage> findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(List<Long> popupIdList);

  List<PopupImage> findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(Long popupId);
}
