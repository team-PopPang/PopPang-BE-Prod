package com.poppang.be.domain.popup.dto.v2.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.poppang.be.domain.popup.entity.PopupSubmission;
import com.poppang.be.domain.popup.entity.PopupSubmissionImage;
import com.poppang.be.domain.popup.entity.PopupSubmissionRecommend;
import com.poppang.be.domain.popup.entity.PopupSubmissionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class V2PopupSubmissionAdminDetailResponseDto {

  private Long popupSubmissionId;
  private String name;
  private LocalDate startDate;
  private LocalDate endDate;
  private String roadAddress;
  private String region;
  private String description;
  private List<Long> recommendIdList;
  private List<RecommendResponse> recommendList;
  private List<ImageResponse> imageList;
  private String address;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
  private LocalTime openTime;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
  private LocalTime closeTime;

  private String instaPostUrl;
  private PopupSubmissionStatus status;

  @Builder
  public V2PopupSubmissionAdminDetailResponseDto(
      Long popupSubmissionId,
      String name,
      LocalDate startDate,
      LocalDate endDate,
      String roadAddress,
      String region,
      String description,
      List<Long> recommendIdList,
      List<RecommendResponse> recommendList,
      List<ImageResponse> imageList,
      String address,
      LocalTime openTime,
      LocalTime closeTime,
      String instaPostUrl,
      PopupSubmissionStatus status) {
    this.popupSubmissionId = popupSubmissionId;
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
    this.roadAddress = roadAddress;
    this.region = region;
    this.description = description;
    this.recommendIdList = recommendIdList;
    this.recommendList = recommendList;
    this.imageList = imageList;
    this.address = address;
    this.openTime = openTime;
    this.closeTime = closeTime;
    this.instaPostUrl = instaPostUrl;
    this.status = status;
  }

  public static V2PopupSubmissionAdminDetailResponseDto from(
      PopupSubmission popupSubmission,
      List<PopupSubmissionImage> imageList,
      List<PopupSubmissionRecommend> recommendList) {
    List<RecommendResponse> recommendResponses =
        recommendList.stream().map(RecommendResponse::from).toList();

    return V2PopupSubmissionAdminDetailResponseDto.builder()
        .popupSubmissionId(popupSubmission.getId())
        .name(popupSubmission.getName())
        .startDate(popupSubmission.getStartDate())
        .endDate(popupSubmission.getEndDate())
        .roadAddress(popupSubmission.getRoadAddress())
        .region(popupSubmission.getRegion())
        .description(popupSubmission.getDescription())
        .recommendIdList(
            recommendResponses.stream().map(RecommendResponse::getRecommendId).toList())
        .recommendList(recommendResponses)
        .imageList(imageList.stream().map(ImageResponse::from).toList())
        .address(popupSubmission.getAddress())
        .openTime(popupSubmission.getOpenTime())
        .closeTime(popupSubmission.getCloseTime())
        .instaPostUrl(popupSubmission.getInstaPostUrl())
        .status(popupSubmission.getStatus())
        .build();
  }

  @Getter
  @NoArgsConstructor
  public static class RecommendResponse {

    private Long recommendId;
    private String recommendName;

    @Builder
    public RecommendResponse(Long recommendId, String recommendName) {
      this.recommendId = recommendId;
      this.recommendName = recommendName;
    }

    private static RecommendResponse from(PopupSubmissionRecommend popupSubmissionRecommend) {
      return RecommendResponse.builder()
          .recommendId(popupSubmissionRecommend.getRecommend().getId())
          .recommendName(popupSubmissionRecommend.getRecommend().getRecommendName())
          .build();
    }
  }

  @Getter
  @NoArgsConstructor
  public static class ImageResponse {

    private String imageUrl;
    private int sortOrder;

    @Builder
    public ImageResponse(String imageUrl, int sortOrder) {
      this.imageUrl = imageUrl;
      this.sortOrder = sortOrder;
    }

    private static ImageResponse from(PopupSubmissionImage popupSubmissionImage) {
      return ImageResponse.builder()
          .imageUrl(popupSubmissionImage.getImageUrl())
          .sortOrder(popupSubmissionImage.getSortOrder())
          .build();
    }
  }
}
