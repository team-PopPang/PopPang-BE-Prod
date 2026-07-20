package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.util.StringNormalizer;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupHomeFilterService {

  private final PopupRepository popupRepository;

  @Transactional(readOnly = true)
  public List<Popup> getFilteredPopupList(
      String region, String district, HomeSortStandard homeSortStandard) {
    String normalizedRegion = StringNormalizer.normalizeRegion(region);
    String normalizedDistrict = StringNormalizer.normalizeDistrict(district);

    if (homeSortStandard == null) {
      throw new BaseException(ErrorCode.INVALID_SORT_STANDARD);
    }

    return switch (homeSortStandard) {
      case NEWEST -> popupRepository.findActiveByNewest(normalizedRegion, normalizedDistrict);
      case CLOSING_SOON -> popupRepository.findActiveByClosingSoon(
          normalizedRegion, normalizedDistrict);
      case MOST_FAVORITED -> popupRepository.findActiveByMostFavorited(
          normalizedRegion, normalizedDistrict);
      case MOST_VIEWED -> popupRepository.findActiveByMostViewed(
          normalizedRegion, normalizedDistrict);
    };
  }
}
