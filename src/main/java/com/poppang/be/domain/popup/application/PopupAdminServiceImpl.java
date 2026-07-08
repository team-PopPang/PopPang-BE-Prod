package com.poppang.be.domain.popup.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionAdminImageRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionAdminUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionStatusUpdateRequestDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminDetailResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminListResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupSubmissionAdminUpdateResponseDto;
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
import com.poppang.be.domain.users.entity.Role;
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
public class PopupAdminServiceImpl implements PopupAdminService {

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
  public void deactivatePopup(String adminUuid, String popupUuid) {
    validateAdmin(adminUuid);

    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    popup.deactivate();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopupSubmissionAdminListResponseDto> getPopupSubmissions(
      String adminUuid, String status) {
    validateAdmin(adminUuid);

    PopupSubmissionStatusFilter statusFilter =
        PopupSubmissionStatusFilter.from(status)
            .orElseThrow(() -> new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_STATUS));
    LocalDate today = LocalDate.now(KOREA_ZONE_ID);

    List<PopupSubmission> popupSubmissionList =
        statusFilter.getStatus() == null
            ? popupSubmissionRepository.findByEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(
                today)
            : popupSubmissionRepository
                .findByStatusAndEndDateGreaterThanEqualOrderByCreatedAtDescIdDesc(
                    statusFilter.getStatus(), today);

    Map<String, String> nicknameByUuid = getSubmitterNicknameMap(popupSubmissionList);

    return popupSubmissionList.stream()
        .map(
            popupSubmission ->
                PopupSubmissionAdminListResponseDto.from(
                    popupSubmission, nicknameByUuid.get(popupSubmission.getSubmitterUserUuid())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PopupSubmissionAdminDetailResponseDto getPopupSubmissionDetail(
      String adminUuid, Long popupSubmissionId) {
    validateAdmin(adminUuid);

    PopupSubmission popupSubmission =
        popupSubmissionRepository
            .findById(popupSubmissionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_SUBMISSION_NOT_FOUND));

    List<PopupSubmissionImage> imageList =
        popupSubmissionImageRepository.findAllByPopupSubmission_IdOrderBySortOrderAscIdAsc(
            popupSubmission.getId());
    List<PopupSubmissionRecommend> recommendList =
        popupSubmissionRecommendRepository.findAllByPopupSubmissionIdWithRecommend(
            popupSubmission.getId());

    return PopupSubmissionAdminDetailResponseDto.from(popupSubmission, imageList, recommendList);
  }

  @Override
  @Transactional
  public PopupSubmissionAdminUpdateResponseDto updatePopupSubmission(
      String adminUuid,
      Long popupSubmissionId,
      PopupSubmissionAdminUpdateRequestDto popupSubmissionAdminUpdateRequestDto,
      List<MultipartFile> images) {
    validateAdmin(adminUuid);

    PopupSubmissionStatus updateStatus =
        parsePopupSubmissionUpdateStatus(popupSubmissionAdminUpdateRequestDto);

    PopupSubmission popupSubmission =
        popupSubmissionRepository
            .findById(popupSubmissionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_SUBMISSION_NOT_FOUND));

    if (popupSubmission.getStatus() != PopupSubmissionStatus.PENDING) {
      throw new BaseException(ErrorCode.POPUP_SUBMISSION_ALREADY_PROCESSED);
    }

    if (updateStatus == PopupSubmissionStatus.REJECTED) {
      popupSubmission.updateStatus(PopupSubmissionStatus.REJECTED);
      return PopupSubmissionAdminUpdateResponseDto.from(null);
    }

    validatePopupSubmissionApprovalRequest(popupSubmissionAdminUpdateRequestDto, images);
    List<Recommend> recommendList =
        getRecommendList(popupSubmissionAdminUpdateRequestDto.getRecommendIdList());

    List<String> storedImageUrlPathList = new ArrayList<>();
    try {
      List<ResolvedPopupImage> imageList =
          resolvePopupImages(
              popupSubmissionAdminUpdateRequestDto.getImageList(), images, storedImageUrlPathList);

      Popup popup = savePopupFromSubmissionUpdateRequest(popupSubmissionAdminUpdateRequestDto);
      savePopupImages(popup, imageList);
      savePopupRecommends(popup, recommendList);

      popupSubmission.updateStatus(PopupSubmissionStatus.APPROVED);
      popupSubmissionRepository.flush();
      return PopupSubmissionAdminUpdateResponseDto.from(popup.getUuid());
    } catch (RuntimeException e) {
      popupSubmissionImageStorage.deleteAll(storedImageUrlPathList);
      throw e;
    }
  }

  private PopupSubmissionStatus parsePopupSubmissionUpdateStatus(
      PopupSubmissionAdminUpdateRequestDto popupSubmissionAdminUpdateRequestDto) {
    if (popupSubmissionAdminUpdateRequestDto == null
        || isBlank(popupSubmissionAdminUpdateRequestDto.getStatus())) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_UPDATE_STATUS);
    }

    String status = popupSubmissionAdminUpdateRequestDto.getStatus().trim();
    if (PopupSubmissionStatus.APPROVED.name().equals(status)) {
      return PopupSubmissionStatus.APPROVED;
    }

    if (PopupSubmissionStatus.REJECTED.name().equals(status)) {
      return PopupSubmissionStatus.REJECTED;
    }

    throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_UPDATE_STATUS);
  }

  private void validatePopupSubmissionApprovalRequest(
      PopupSubmissionAdminUpdateRequestDto popupSubmissionAdminUpdateRequestDto,
      List<MultipartFile> images) {
    if (popupSubmissionAdminUpdateRequestDto == null
        || isBlank(popupSubmissionAdminUpdateRequestDto.getName())
        || popupSubmissionAdminUpdateRequestDto.getStartDate() == null
        || popupSubmissionAdminUpdateRequestDto.getEndDate() == null
        || isBlank(popupSubmissionAdminUpdateRequestDto.getRoadAddress())
        || isBlank(popupSubmissionAdminUpdateRequestDto.getRegion())
        || isBlank(popupSubmissionAdminUpdateRequestDto.getAddress())
        || popupSubmissionAdminUpdateRequestDto.getLatitude() == null
        || popupSubmissionAdminUpdateRequestDto.getLongitude() == null
        || isBlank(popupSubmissionAdminUpdateRequestDto.getCaptionSummary())
        || isBlank(popupSubmissionAdminUpdateRequestDto.getCaption())
        || popupSubmissionAdminUpdateRequestDto.getImageList() == null
        || popupSubmissionAdminUpdateRequestDto.getImageList().isEmpty()
        || popupSubmissionAdminUpdateRequestDto.getRecommendIdList() == null
        || popupSubmissionAdminUpdateRequestDto.getRecommendIdList().isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }

    validatePopupImagesRequest(popupSubmissionAdminUpdateRequestDto.getImageList(), images);

    for (Long recommendId : popupSubmissionAdminUpdateRequestDto.getRecommendIdList()) {
      if (recommendId == null) {
        throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
      }
    }

    parseMediaType(popupSubmissionAdminUpdateRequestDto.getMediaType());
  }

  private Popup savePopupFromSubmissionUpdateRequest(
      PopupSubmissionAdminUpdateRequestDto popupSubmissionAdminUpdateRequestDto) {
    Popup popup =
        Popup.builder()
            .name(popupSubmissionAdminUpdateRequestDto.getName())
            .startDate(popupSubmissionAdminUpdateRequestDto.getStartDate())
            .endDate(popupSubmissionAdminUpdateRequestDto.getEndDate())
            .openTime(popupSubmissionAdminUpdateRequestDto.getOpenTime())
            .closeTime(popupSubmissionAdminUpdateRequestDto.getCloseTime())
            .address(popupSubmissionAdminUpdateRequestDto.getAddress())
            .roadAddress(popupSubmissionAdminUpdateRequestDto.getRoadAddress())
            .region(popupSubmissionAdminUpdateRequestDto.getRegion())
            .latitude(popupSubmissionAdminUpdateRequestDto.getLatitude())
            .longitude(popupSubmissionAdminUpdateRequestDto.getLongitude())
            .geocodingQuery(popupSubmissionAdminUpdateRequestDto.getGeocodingQuery())
            .instaPostId(popupSubmissionAdminUpdateRequestDto.getInstaPostId())
            .instaPostUrl(popupSubmissionAdminUpdateRequestDto.getInstaPostUrl())
            .captionSummary(popupSubmissionAdminUpdateRequestDto.getCaptionSummary())
            .caption(popupSubmissionAdminUpdateRequestDto.getCaption())
            .mediaType(parseMediaType(popupSubmissionAdminUpdateRequestDto.getMediaType()))
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
    } catch (IllegalArgumentException e) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }
  }

  private void validatePopupImagesRequest(
      List<PopupSubmissionAdminImageRequestDto> imageRequestList, List<MultipartFile> images) {
    for (PopupSubmissionAdminImageRequestDto image : imageRequestList) {
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
    } catch (IllegalArgumentException e) {
      throw new BaseException(ErrorCode.INVALID_POPUP_SUBMISSION_REQUEST);
    }
  }

  private List<ResolvedPopupImage> resolvePopupImages(
      List<PopupSubmissionAdminImageRequestDto> imageRequestList,
      List<MultipartFile> images,
      List<String> storedImageUrlPathList) {
    Map<Integer, String> uploadImageUrlByFileIndex =
        storeUploadImages(imageRequestList, images, storedImageUrlPathList);

    List<ResolvedPopupImage> resolvedImageList = new ArrayList<>();
    for (int i = 0; i < imageRequestList.size(); i++) {
      PopupSubmissionAdminImageRequestDto image = imageRequestList.get(i);
      PopupSubmissionAdminImageSourceType sourceType = parseImageSourceType(image.getSourceType());
      String imageUrl =
          sourceType == PopupSubmissionAdminImageSourceType.EXISTING
              ? image.getImageUrl()
              : uploadImageUrlByFileIndex.get(image.getFileIndex());
      int sortOrder = image.getSortOrder() != null ? image.getSortOrder() : i;

      resolvedImageList.add(new ResolvedPopupImage(imageUrl, sortOrder, i));
    }

    resolvedImageList.sort(
        Comparator.comparingInt(ResolvedPopupImage::sortOrder)
            .thenComparingInt(ResolvedPopupImage::originalIndex));
    return resolvedImageList;
  }

  private Map<Integer, String> storeUploadImages(
      List<PopupSubmissionAdminImageRequestDto> imageRequestList,
      List<MultipartFile> images,
      List<String> storedImageUrlPathList) {
    Set<Integer> uploadFileIndexSet = new LinkedHashSet<>();
    for (PopupSubmissionAdminImageRequestDto image : imageRequestList) {
      if (parseImageSourceType(image.getSourceType())
          == PopupSubmissionAdminImageSourceType.UPLOAD) {
        uploadFileIndexSet.add(image.getFileIndex());
      }
    }

    if (uploadFileIndexSet.isEmpty()) {
      return Map.of();
    }

    List<Integer> uploadFileIndexList = new ArrayList<>(uploadFileIndexSet);
    List<MultipartFile> uploadImageList = uploadFileIndexList.stream().map(images::get).toList();
    List<String> uploadedImageUrlPathList = popupSubmissionImageStorage.storeAll(uploadImageList);
    storedImageUrlPathList.addAll(uploadedImageUrlPathList);

    Map<Integer, String> uploadImageUrlByFileIndex = new HashMap<>();
    for (int i = 0; i < uploadFileIndexList.size(); i++) {
      uploadImageUrlByFileIndex.put(uploadFileIndexList.get(i), uploadedImageUrlPathList.get(i));
    }
    return uploadImageUrlByFileIndex;
  }

  private void savePopupImages(Popup popup, List<ResolvedPopupImage> imageRequestList) {
    List<PopupImage> imageList = new ArrayList<>();
    for (ResolvedPopupImage image : imageRequestList) {
      imageList.add(
          PopupImage.builder()
              .popup(popup)
              .imageUrl(image.imageUrl())
              .sortOrder(image.sortOrder())
              .build());
    }

    popupImageRepository.saveAllAndFlush(imageList);
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

  private void savePopupRecommends(Popup popup, List<Recommend> recommendList) {
    List<PopupRecommend> popupRecommendList = new ArrayList<>();
    for (Recommend recommend : recommendList) {
      popupRecommendList.add(PopupRecommend.builder().popup(popup).recommend(recommend).build());
    }

    popupRecommendRepository.saveAllAndFlush(popupRecommendList);
  }

  private void validateAdmin(String adminUuid) {
    if (adminUuid == null || adminUuid.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_ADMIN_USER_UUID);
    }

    Users admin =
        usersRepository
            .findByUuid(adminUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    if (admin.getRole() != Role.ADMIN) {
      throw new BaseException(ErrorCode.ACCESS_DENIED);
    }
  }

  private Map<String, String> getSubmitterNicknameMap(List<PopupSubmission> popupSubmissionList) {
    List<String> submitterUuidList =
        popupSubmissionList.stream()
            .map(PopupSubmission::getSubmitterUserUuid)
            .filter(uuid -> uuid != null && !uuid.isBlank())
            .distinct()
            .toList();

    if (submitterUuidList.isEmpty()) {
      return Map.of();
    }

    Map<String, String> nicknameByUuid = new HashMap<>();
    for (Users user : usersRepository.findByUuidIn(submitterUuidList)) {
      nicknameByUuid.put(user.getUuid(), user.getNickname());
    }

    return nicknameByUuid;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  @Override
  @Transactional
  public void updateSubmissionStatus(
      Long submissionId,
      PopupSubmissionStatusUpdateRequestDto popupSubmissionStatusUpdateRequestDto) {
    PopupSubmission popupSubmission =
        popupSubmissionRepository
            .findById(submissionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    if (popupSubmission.getStatus() != PopupSubmissionStatus.PENDING) {
      throw new BaseException(ErrorCode.FAVORITE_ALREADY_EXISTS);
    }
    popupSubmission.updateStatus(popupSubmissionStatusUpdateRequestDto.getPopupSubmissionStatus());
  }
}
