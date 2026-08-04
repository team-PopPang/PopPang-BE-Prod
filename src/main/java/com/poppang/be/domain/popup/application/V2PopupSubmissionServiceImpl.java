package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.v2.V2PopupSubmissionCreateRequestDto;
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
public class V2PopupSubmissionServiceImpl implements V2PopupSubmissionService {

  private final UsersRepository usersRepository;
  private final PopupSubmissionRepository popupSubmissionRepository;
  private final PopupSubmissionImageRepository popupSubmissionImageRepository;
  private final PopupSubmissionRecommendRepository popupSubmissionRecommendRepository;
  private final RecommendRepository recommendRepository;
  private final PopupSubmissionImageStorage popupSubmissionImageStorage;

  @Override
  @Transactional
  public void createPopupSubmission(
      String userUuid,
      V2PopupSubmissionCreateRequestDto popupSubmissionCreateRequestDto,
      List<MultipartFile> images) {
    validatePopupSubmissionCreateRequest(userUuid, popupSubmissionCreateRequestDto, images);
    usersRepository
        .findByUuidAndDeletedFalse(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<Recommend> recommendList =
        getRecommendList(popupSubmissionCreateRequestDto.getRecommendIdList());
    List<String> imageUrlPathList = popupSubmissionImageStorage.storeAll(images);
    try {
      PopupSubmission popupSubmission =
          popupSubmissionRepository.saveAndFlush(
              popupSubmissionCreateRequestDto.toEntity(userUuid));
      savePopupSubmissionImages(popupSubmission, imageUrlPathList);
      savePopupSubmissionRecommends(popupSubmission, recommendList);
    } catch (RuntimeException exception) {
      popupSubmissionImageStorage.deleteAll(imageUrlPathList);
      throw exception;
    }
  }

  private void validatePopupSubmissionCreateRequest(
      String userUuid, V2PopupSubmissionCreateRequestDto request, List<MultipartFile> images) {
    if (isBlank(userUuid)
        || request == null
        || isBlank(request.getName())
        || request.getStartDate() == null
        || request.getEndDate() == null
        || isBlank(request.getRoadAddress())
        || isBlank(request.getRegion())
        || isBlank(request.getDescription())
        || images == null
        || images.isEmpty()
        || request.getRecommendIdList() == null
        || request.getRecommendIdList().isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }
    for (MultipartFile image : images) {
      if (image == null || image.isEmpty()) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
    }
    for (Long recommendId : request.getRecommendIdList()) {
      if (recommendId == null) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
    }
  }

  private List<Recommend> getRecommendList(List<Long> recommendIdList) {
    Set<Long> recommendIds = new LinkedHashSet<>(recommendIdList);
    List<Recommend> recommends = recommendRepository.findAllById(recommendIds);
    if (recommends.size() != recommendIds.size()) {
      throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
    }

    Map<Long, Recommend> recommendById = new HashMap<>();
    for (Recommend recommend : recommends) {
      recommendById.put(recommend.getId(), recommend);
    }
    List<Recommend> sortedRecommends = new ArrayList<>();
    for (Long recommendId : recommendIds) {
      Recommend recommend = recommendById.get(recommendId);
      if (recommend == null) {
        throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
      }
      sortedRecommends.add(recommend);
    }
    return sortedRecommends;
  }

  private void savePopupSubmissionImages(
      PopupSubmission popupSubmission, List<String> imageUrlPathList) {
    List<PopupSubmissionImage> images = new ArrayList<>();
    for (int index = 0; index < imageUrlPathList.size(); index++) {
      images.add(
          PopupSubmissionImage.builder()
              .popupSubmission(popupSubmission)
              .imageUrl(imageUrlPathList.get(index))
              .sortOrder(index)
              .build());
    }
    popupSubmissionImageRepository.saveAllAndFlush(images);
  }

  private void savePopupSubmissionRecommends(
      PopupSubmission popupSubmission, List<Recommend> recommends) {
    List<PopupSubmissionRecommend> popupSubmissionRecommends = new ArrayList<>();
    for (Recommend recommend : recommends) {
      popupSubmissionRecommends.add(
          PopupSubmissionRecommend.builder()
              .popupSubmission(popupSubmission)
              .recommend(recommend)
              .build());
    }
    popupSubmissionRecommendRepository.saveAllAndFlush(popupSubmissionRecommends);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
