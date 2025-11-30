package com.poppang.be.domain.popup.mapper;

import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.response.PopupResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PopupResponseDtoMapper {

    private final PopupImageRepository popupImageRepository;
    private final PopupRecommendRepository popupRecommendRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final PopupTotalViewCountRepository popupTotalViewCountRepository;

    public List<PopupResponseDto> toPopupResponseDtoList(List<Popup> popupList) {

        if (popupList == null || popupList.isEmpty()) {
            return List.of();
        }

        // id/uuid 수집
        List<Long> popupIdList = new ArrayList<>(popupList.size());
        List<String> popupUuidList = new ArrayList<>(popupList.size());
        for (Popup popup : popupList) {
            popupIdList.add(popup.getId());
            popupUuidList.add(popup.getUuid());
        }

        // 팝업 이미지
        List<PopupImage> images = popupImageRepository.findAllByPopup_IdInOrderByPopup_IdAscSortOrderAsc(popupIdList);
        Map<Long, List<String>> imageMap = new HashMap<>();
        for (PopupImage img : images) {
            imageMap.computeIfAbsent(img.getPopup().getId(), k -> new ArrayList<>()).add(img.getImageUrl());
        }

        // 추천
        List<PopupRecommend> recs = popupRecommendRepository.findAllByPopup_IdIn(popupIdList);
        Map<Long, List<String>> recommendMap = new HashMap<>();
        for (PopupRecommend r : recs) {
            recommendMap
                    .computeIfAbsent(r.getPopup().getId(), k -> new ArrayList<>())
                    .add(r.getRecommend().getRecommendName());
        }

        // 좋아요 수 배치
        Map<Long, Long> favoriteCountMap = new HashMap<>();
        for (var row : userFavoriteRepository.countAllByPopupIds(popupIdList)) {
            favoriteCountMap.put(row.getPopupId(), row.getCnt());
        }

        // 조회수 배치
        Map<String, Long> viewCountMap = new HashMap<>();
        for (var row : popupTotalViewCountRepository.findAllViewCounts(popupUuidList)) {
            viewCountMap.put(row.getPopupUuid(),
                    row.getViewCount() == null ? 0L : row.getViewCount());
        }

        // DTO 조립
        List<PopupResponseDto> popupResponseDtoList = new ArrayList<>(popupList.size());
        for (Popup popup : popupList) {
            popupResponseDtoList.add(PopupResponseDto.builder()
                    .popupUuid(popup.getUuid())
                    .name(popup.getName())
                    .startDate(popup.getStartDate())
                    .endDate(popup.getEndDate())
                    .openTime(popup.getOpenTime())
                    .closeTime(popup.getCloseTime())
                    .address(popup.getAddress())
                    .roadAddress(popup.getRoadAddress())
                    .region(popup.getRegion())
                    .latitude(popup.getLatitude())
                    .longitude(popup.getLongitude())
                    .instaPostId(popup.getInstaPostId())
                    .instaPostUrl(popup.getInstaPostUrl())
                    .captionSummary(popup.getCaptionSummary())
                    .imageUrlList(imageMap.getOrDefault(popup.getId(), List.of()))
                    .mediaType(popup.getMediaType())
                    .recommendList(recommendMap.getOrDefault(popup.getId(), null))
                    .favoriteCount(favoriteCountMap.getOrDefault(popup.getId(), 0L))
                    .viewCount(viewCountMap.getOrDefault(popup.getUuid(), 0L))
                    .build());
        }
        return popupResponseDtoList;
    }

}

