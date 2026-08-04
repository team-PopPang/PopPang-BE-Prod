package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminImageRequestDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminListResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionAdminUpdateResponseDto;
import com.poppang.be.domain.popup.dto.v2.admin.V2PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.entity.MediaType;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionImage;
import com.poppang.be.domain.popup.entity.PopupSubmissionRecommend;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import com.poppang.be.domain.popup.enums.PopupSubmissionStatusFilter;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupSubmissionRepository;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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
public class V2PopupAdminServiceImpl implements V2PopupAdminService {

  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;
  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final PopupSubmissionRepository popupSubmissionRepository;
  private final PopupSubmissionImageRepository popupSubmissionImageRepository;
  private final PopupSubmissionRecommendRepository popupSubmissionRecommendRepository;
  private final RecommendRepository recommendRepository;
  private final PopupSubmissionImageStorage popupSubmissionImageStorage;

  private enum PopupSubmissionAdminImageSourceType {
    EXISTING,
    UPLOAD
  }

  private record ResolvedPopupImage(String imageUrl, int sortOrder, int originalIndex) {}

  @Override
  @Transactional
  public void deactivatePopup(String popupUuid) {
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));
    popup.deactivate();
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2PopupSubmissionAdminListResponseDto> getPopupSubmissions(String status) {
    PopupSubmissionStatusFilter statusFilter =
        PopupSubmissionStatusFilter.from(status)
            .orElseThrow(() -> new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_STATUS));
    LocalDate today = LocalDate.now(KOREA_ZONE_ID);

    List<PopupSubmission> popupSubmissions =
        statusFilter.getStatus() == null
            ? popupSubmissionRepository.findByEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(
                today)
            : popupSubmissionRepository
                .findByStatusAndEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(
                    statusFilter.getStatus(), today);
    Map<String, String> nicknameByUuid = getSubmitterNicknameMap(popupSubmissions);

    return popupSubmissions.stream()
        .map(
            popupSubmission ->
                V2PopupSubmissionAdminListResponseDto.from(
                    popupSubmission, nicknameByUuid.get(popupSubmission.getSubmitterUserUuid())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public V2PopupSubmissionAdminDetailResponseDto getPopupSubmissionDetail(Long popupSubmissionId) {
    PopupSubmission popupSubmission =
        popupSubmissionRepository
            .findById(popupSubmissionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_SUBMISSION_NOT_FOUND));
    List<PopupSubmissionImage> images =
        popupSubmissionImageRepository.findAllByPopupSubmission_IdOrderBySortOrderAscIdAsc(
            popupSubmission.getId());
    List<PopupSubmissionRecommend> recommends =
        popupSubmissionRecommendRepository.findAllByPopupSubmissionIdWithRecommend(
            popupSubmission.getId());

    return V2PopupSubmissionAdminDetailResponseDto.from(popupSubmission, images, recommends);
  }

  @Override
  @Transactional
  public V2PopupSubmissionAdminUpdateResponseDto updatePopupSubmission(
      Long popupSubmissionId,
      V2PopupSubmissionAdminUpdateRequestDto request,
      List<MultipartFile> images) {
    PopupSubmissionStatus updateStatus = parsePopupSubmissionUpdateStatus(request);
    PopupSubmission popupSubmission =
        popupSubmissionRepository
            .findById(popupSubmissionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_SUBMISSION_NOT_FOUND));

    if (popupSubmission.getStatus() != PopupSubmissionStatus.PENDING) {
      throw new BaseException(ErrorCode.POPUP_SUBMISSION_ALREADY_PROCESSED);
    }
    if (updateStatus == PopupSubmissionStatus.REJECTED) {
      popupSubmission.updateStatus(PopupSubmissionStatus.REJECTED);
      return V2PopupSubmissionAdminUpdateResponseDto.from(null);
    }

    validatePopupSubmissionApprovalRequest(request, images);
    List<Recommend> recommends = getRecommendList(request.getRecommendIdList());
    List<String> storedImageUrlPaths = new ArrayList<>();
    try {
      List<ResolvedPopupImage> resolvedImages =
          resolvePopupImages(request.getImageList(), images, storedImageUrlPaths);
      Popup popup = savePopupFromSubmissionUpdateRequest(request);
      savePopupImages(popup, resolvedImages);
      savePopupRecommends(popup, recommends);

      popupSubmission.updateStatus(PopupSubmissionStatus.APPROVED);
      popupSubmissionRepository.flush();
      return V2PopupSubmissionAdminUpdateResponseDto.from(popup.getUuid());
    } catch (RuntimeException exception) {
      popupSubmissionImageStorage.deleteAll(storedImageUrlPaths);
      throw exception;
    }
  }

  @Override
  @Transactional
  public void updateSubmissionStatus(
      Long submissionId, V2PopupSubmissionStatusUpdateRequestDto request) {
    PopupSubmission popupSubmission =
        popupSubmissionRepository
            .findById(submissionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));
    if (popupSubmission.getStatus() != PopupSubmissionStatus.PENDING) {
      throw new BaseException(ErrorCode.FAVORITE_ALREADY_EXISTS);
    }
    popupSubmission.updateStatus(request.getPopupSubmissionStatus());
  }

  private PopupSubmissionStatus parsePopupSubmissionUpdateStatus(
      V2PopupSubmissionAdminUpdateRequestDto request) {
    if (request == null || isBlank(request.getStatus())) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_UPDATE_STATUS);
    }
    String status = request.getStatus().trim();
    if (PopupSubmissionStatus.APPROVED.name().equals(status)) {
      return PopupSubmissionStatus.APPROVED;
    }
    if (PopupSubmissionStatus.REJECTED.name().equals(status)) {
      return PopupSubmissionStatus.REJECTED;
    }
    throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_UPDATE_STATUS);
  }

  private void validatePopupSubmissionApprovalRequest(
      V2PopupSubmissionAdminUpdateRequestDto request, List<MultipartFile> images) {
    if (request == null
        || isBlank(request.getName())
        || request.getStartDate() == null
        || request.getEndDate() == null
        || isBlank(request.getRoadAddress())
        || isBlank(request.getRegion())
        || isBlank(request.getAddress())
        || request.getLatitude() == null
        || request.getLongitude() == null
        || isBlank(request.getCaptionSummary())
        || isBlank(request.getCaption())
        || request.getImageList() == null
        || request.getImageList().isEmpty()
        || request.getRecommendIdList() == null
        || request.getRecommendIdList().isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }

    validatePopupImagesRequest(request.getImageList(), images);
    for (Long recommendId : request.getRecommendIdList()) {
      if (recommendId == null) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
    }
    parseMediaType(request.getMediaType());
  }

  private Popup savePopupFromSubmissionUpdateRequest(
      V2PopupSubmissionAdminUpdateRequestDto request) {
    Popup popup =
        Popup.builder()
            .name(request.getName())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .openTime(request.getOpenTime())
            .closeTime(request.getCloseTime())
            .address(request.getAddress())
            .roadAddress(request.getRoadAddress())
            .region(request.getRegion())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .geocodingQuery(request.getGeocodingQuery())
            .instaPostId(request.getInstaPostId())
            .instaPostUrl(request.getInstaPostUrl())
            .captionSummary(request.getCaptionSummary())
            .caption(request.getCaption())
            .mediaType(parseMediaType(request.getMediaType()))
            .activated(true)
            .build();
    return popupRepository.saveAndFlush(popup);
  }

  private MediaType parseMediaType(String mediaType) {
    if (isBlank(mediaType)) {
      return null;
    }
    try {
      return MediaType.valueOf(mediaType.trim());
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }
  }

  private void validatePopupImagesRequest(
      List<V2PopupSubmissionAdminImageRequestDto> imageRequests, List<MultipartFile> images) {
    for (V2PopupSubmissionAdminImageRequestDto image : imageRequests) {
      if (image == null) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
      PopupSubmissionAdminImageSourceType sourceType = parseImageSourceType(image.getSourceType());
      if (sourceType == PopupSubmissionAdminImageSourceType.EXISTING) {
        if (isBlank(image.getImageUrl())) {
          throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
        }
        continue;
      }
      if (images == null
          || images.isEmpty()
          || image.getFileIndex() == null
          || image.getFileIndex() < 0
          || image.getFileIndex() >= images.size()) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
      MultipartFile uploadImage = images.get(image.getFileIndex());
      if (uploadImage == null || uploadImage.isEmpty()) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
    }
  }

  private PopupSubmissionAdminImageSourceType parseImageSourceType(String sourceType) {
    if (isBlank(sourceType)) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }
    try {
      return PopupSubmissionAdminImageSourceType.valueOf(sourceType.trim());
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }
  }

  private List<ResolvedPopupImage> resolvePopupImages(
      List<V2PopupSubmissionAdminImageRequestDto> imageRequests,
      List<MultipartFile> images,
      List<String> storedImageUrlPaths) {
    Map<Integer, String> uploadImageUrlByFileIndex =
        storeUploadImages(imageRequests, images, storedImageUrlPaths);
    List<ResolvedPopupImage> resolvedImages = new ArrayList<>();
    for (int index = 0; index < imageRequests.size(); index++) {
      V2PopupSubmissionAdminImageRequestDto image = imageRequests.get(index);
      PopupSubmissionAdminImageSourceType sourceType = parseImageSourceType(image.getSourceType());
      String imageUrl =
          sourceType == PopupSubmissionAdminImageSourceType.EXISTING
              ? image.getImageUrl()
              : uploadImageUrlByFileIndex.get(image.getFileIndex());
      int sortOrder = image.getSortOrder() != null ? image.getSortOrder() : index;
      resolvedImages.add(new ResolvedPopupImage(imageUrl, sortOrder, index));
    }
    resolvedImages.sort(
        Comparator.comparingInt(ResolvedPopupImage::sortOrder)
            .thenComparingInt(ResolvedPopupImage::originalIndex));
    return resolvedImages;
  }

  private Map<Integer, String> storeUploadImages(
      List<V2PopupSubmissionAdminImageRequestDto> imageRequests,
      List<MultipartFile> images,
      List<String> storedImageUrlPaths) {
    Set<Integer> uploadFileIndexes = new LinkedHashSet<>();
    for (V2PopupSubmissionAdminImageRequestDto image : imageRequests) {
      if (parseImageSourceType(image.getSourceType())
          == PopupSubmissionAdminImageSourceType.UPLOAD) {
        uploadFileIndexes.add(image.getFileIndex());
      }
    }
    if (uploadFileIndexes.isEmpty()) {
      return Map.of();
    }

    List<Integer> uploadFileIndexList = new ArrayList<>(uploadFileIndexes);
    List<MultipartFile> uploadImages = uploadFileIndexList.stream().map(images::get).toList();
    List<String> uploadedImageUrlPaths = popupSubmissionImageStorage.storeAll(uploadImages);
    storedImageUrlPaths.addAll(uploadedImageUrlPaths);

    Map<Integer, String> uploadImageUrlByFileIndex = new HashMap<>();
    for (int index = 0; index < uploadFileIndexList.size(); index++) {
      uploadImageUrlByFileIndex.put(
          uploadFileIndexList.get(index), uploadedImageUrlPaths.get(index));
    }
    return uploadImageUrlByFileIndex;
  }

  private void savePopupImages(Popup popup, List<ResolvedPopupImage> resolvedImages) {
    List<PopupImage> images = new ArrayList<>();
    for (ResolvedPopupImage image : resolvedImages) {
      images.add(
          PopupImage.builder()
              .popup(popup)
              .imageUrl(image.imageUrl())
              .sortOrder(image.sortOrder())
              .build());
    }
    popupImageRepository.saveAllAndFlush(images);
  }

  private List<Recommend> getRecommendList(List<Long> recommendIds) {
    Set<Long> uniqueRecommendIds = new LinkedHashSet<>(recommendIds);
    List<Recommend> recommends = recommendRepository.findAllById(uniqueRecommendIds);
    if (recommends.size() != uniqueRecommendIds.size()) {
      throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
    }

    Map<Long, Recommend> recommendById = new HashMap<>();
    for (Recommend recommend : recommends) {
      recommendById.put(recommend.getId(), recommend);
    }
    List<Recommend> sortedRecommends = new ArrayList<>();
    for (Long recommendId : uniqueRecommendIds) {
      Recommend recommend = recommendById.get(recommendId);
      if (recommend == null) {
        throw new BaseException(ErrorCode.INVALID_RECOMMEND_ID);
      }
      sortedRecommends.add(recommend);
    }
    return sortedRecommends;
  }

  private void savePopupRecommends(Popup popup, List<Recommend> recommends) {
    List<PopupRecommend> popupRecommends = new ArrayList<>();
    for (Recommend recommend : recommends) {
      popupRecommends.add(PopupRecommend.builder().popup(popup).recommend(recommend).build());
    }
    popupRecommendRepository.saveAllAndFlush(popupRecommends);
  }

  private Map<String, String> getSubmitterNicknameMap(List<PopupSubmission> popupSubmissions) {
    List<String> submitterUuids =
        popupSubmissions.stream()
            .map(PopupSubmission::getSubmitterUserUuid)
            .filter(uuid -> uuid != null && !uuid.isBlank())
            .distinct()
            .toList();
    if (submitterUuids.isEmpty()) {
      return Map.of();
    }

    Map<String, String> nicknameByUuid = new HashMap<>();
    for (Users user : usersRepository.findByUuidIn(submitterUuids)) {
      nicknameByUuid.put(user.getUuid(), user.getNickname());
    }
    return nicknameByUuid;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
