package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PopupSubmissionServiceImpl implements PopupSubmissionService {

  private final UsersRepository usersRepository;
  private final PopupSubmissionRepository popupSubmissionRepository;
  private final PopupSubmissionImageRepository popupSubmissionImageRepository;
  private final PopupSubmissionRecommendRepository popupSubmissionRecommendRepository;
  private final RecommendRepository recommendRepository;
  private final PopupSubmissionImageStorage popupSubmissionImageStorage;

  @Override
  @Transactional
  public void createPopupSubmission(
      PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto, List<MultipartFile> images) {
    validatePopupSubmissionCreateRequest(popupSubmissionCreateRequestDto, images);

    String userUuid = popupSubmissionCreateRequestDto.getUserUuid();

    usersRepository
        .findByUuidAndDeletedFalse(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<Recommend> recommendList =
        getRecommendList(popupSubmissionCreateRequestDto.getRecommendIdList());
    List<String> imageUrlPathList = popupSubmissionImageStorage.storeAll(images);

    try {
      PopupSubmission popupSubmission =
          popupSubmissionRepository.saveAndFlush(popupSubmissionCreateRequestDto.toEntity());
      savePopupSubmissionImages(popupSubmission, imageUrlPathList);
      savePopupSubmissionRecommends(popupSubmission, recommendList);
    } catch (RuntimeException e) {
      popupSubmissionImageStorage.deleteAll(imageUrlPathList);
      throw e;
    }
  }

  private void validatePopupSubmissionCreateRequest(
      PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto, List<MultipartFile> images) {
    if (popupSubmissionCreateRequestDto == null
        || isBlank(popupSubmissionCreateRequestDto.getUserUuid())
        || isBlank(popupSubmissionCreateRequestDto.getName())
        || popupSubmissionCreateRequestDto.getStartDate() == null
        || popupSubmissionCreateRequestDto.getEndDate() == null
        || isBlank(popupSubmissionCreateRequestDto.getRoadAddress())
        || isBlank(popupSubmissionCreateRequestDto.getRegion())
        || isBlank(popupSubmissionCreateRequestDto.getDescription())
        || images == null
        || images.isEmpty()
        || popupSubmissionCreateRequestDto.getRecommendIdList() == null
        || popupSubmissionCreateRequestDto.getRecommendIdList().isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }

    for (MultipartFile image : images) {
      if (image == null || image.isEmpty()) {
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
      PopupSubmission popupSubmission, List<String> imageUrlPathList) {
    List<PopupSubmissionImage> imageList = new ArrayList<>();
    for (int i = 0; i < imageUrlPathList.size(); i++) {
      imageList.add(
          PopupSubmissionImage.builder()
              .popupSubmission(popupSubmission)
              .imageUrl(imageUrlPathList.get(i))
              .sortOrder(i)
              .build());
    }

    popupSubmissionImageRepository.saveAllAndFlush(imageList);
  }

  private List<Recommend> getRecommendList(List<Long> recommendIdList) {
    Set<Long> recommendIdSet = new LinkedHashSet<>(recommendIdList);
    List<Recommend> recommendList = recommendRepository.findAllById(recommendIdSet);
    if (recommendList.size() != recommendIdSet.size()) {
      throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
    }

    Map<Long, Recommend> recommendById = new HashMap<>();
    for (Recommend recommend : recommendList) {
      recommendById.put(recommend.getId(), recommend);
    }

    List<Recommend> sortedRecommendList = new ArrayList<>();
    for (Long recommendId : recommendIdSet) {
      Recommend recommend = recommendById.get(recommendId);
      if (recommend == null) {
        throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
      }
      sortedRecommendList.add(recommend);
    }

    return sortedRecommendList;
  }

  private void savePopupSubmissionRecommends(
      PopupSubmission popupSubmission, List<Recommend> recommendList) {
    List<PopupSubmissionRecommend> popupSubmissionRecommendList = new ArrayList<>();
    for (Recommend recommend : recommendList) {
      popupSubmissionRecommendList.add(
          PopupSubmissionRecommend.builder()
              .popupSubmission(popupSubmission)
              .recommend(recommend)
              .build());
    }

    popupSubmissionRecommendRepository.saveAllAndFlush(popupSubmissionRecommendList);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
