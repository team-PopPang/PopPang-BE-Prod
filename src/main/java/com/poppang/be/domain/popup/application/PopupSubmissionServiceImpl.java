package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionImageRequestDto;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionImage;
import com.poppang.be.domain.popup.entity.PopupSubmissionRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupSubmissionServiceImpl implements PopupSubmissionService {

  private final UsersRepository usersRepository;
  private final PopupSubmissionRepository popupSubmissionRepository;
  private final PopupSubmissionImageRepository popupSubmissionImageRepository;
  private final PopupSubmissionRecommendRepository popupSubmissionRecommendRepository;
  private final RecommendRepository recommendRepository;

  @Override
  @Transactional
  public void createPopupSubmission(
      PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto) {
    validatePopupSubmissionCreateRequest(popupSubmissionCreateRequestDto);

    String userUuid = popupSubmissionCreateRequestDto.getUserUuid();

    usersRepository
        .findByUuidAndDeletedFalse(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    PopupSubmission popupSubmission = popupSubmissionCreateRequestDto.toEntity();
    popupSubmissionRepository.save(popupSubmission);

    savePopupSubmissionImages(popupSubmission, popupSubmissionCreateRequestDto.getImageList());
    savePopupSubmissionRecommends(
        popupSubmission, popupSubmissionCreateRequestDto.getRecommendIdList());
  }

  private void validatePopupSubmissionCreateRequest(
      PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto) {
    if (popupSubmissionCreateRequestDto == null
        || isBlank(popupSubmissionCreateRequestDto.getUserUuid())
        || isBlank(popupSubmissionCreateRequestDto.getName())
        || popupSubmissionCreateRequestDto.getStartDate() == null
        || popupSubmissionCreateRequestDto.getEndDate() == null
        || isBlank(popupSubmissionCreateRequestDto.getRoadAddress())
        || isBlank(popupSubmissionCreateRequestDto.getRegion())
        || isBlank(popupSubmissionCreateRequestDto.getDescription())
        || popupSubmissionCreateRequestDto.getImageList() == null
        || popupSubmissionCreateRequestDto.getImageList().isEmpty()
        || popupSubmissionCreateRequestDto.getRecommendIdList() == null
        || popupSubmissionCreateRequestDto.getRecommendIdList().isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }

    for (PopupSubmissionImageRequestDto image : popupSubmissionCreateRequestDto.getImageList()) {
      if (image == null || isBlank(image.getImageUrl())) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
    }

    for (Long recommendId : popupSubmissionCreateRequestDto.getRecommendIdList()) {
      if (recommendId == null) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
    }
  }

  private void savePopupSubmissionImages(
      PopupSubmission popupSubmission, List<PopupSubmissionImageRequestDto> imageRequestList) {
    List<PopupSubmissionImage> imageList = new ArrayList<>();
    for (int i = 0; i < imageRequestList.size(); i++) {
      PopupSubmissionImageRequestDto image = imageRequestList.get(i);
      imageList.add(
          PopupSubmissionImage.builder()
              .popupSubmission(popupSubmission)
              .imageUrl(image.getImageUrl())
              .sortOrder(image.getSortOrder() != null ? image.getSortOrder() : i)
              .build());
    }

    popupSubmissionImageRepository.saveAll(imageList);
  }

  private void savePopupSubmissionRecommends(
      PopupSubmission popupSubmission, List<Long> recommendIdList) {
    Set<Long> recommendIdSet = new HashSet<>(recommendIdList);
    List<Recommend> recommendList = recommendRepository.findAllById(recommendIdSet);
    if (recommendList.size() != recommendIdSet.size()) {
      throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
    }

    List<PopupSubmissionRecommend> popupSubmissionRecommendList = new ArrayList<>();
    for (Recommend recommend : recommendList) {
      popupSubmissionRecommendList.add(
          PopupSubmissionRecommend.builder()
              .popupSubmission(popupSubmission)
              .recommend(recommend)
              .build());
    }

    popupSubmissionRecommendRepository.saveAll(popupSubmissionRecommendList);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
