package com.poppang.be.domain.popup.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.util.StringNormalizer;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.request.PopupImageUpsertRequestDto;
import com.poppang.be.domain.popup.dto.request.PopupRegisterRequestDto;
import com.poppang.be.domain.popup.dto.response.PopupResponseDto;
import com.poppang.be.domain.popup.dto.response.RegionDistrictsResponse;
import com.poppang.be.domain.popup.entity.MediaType;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupImage;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.enums.SortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.mapper.PopupResponseDtoMapper;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.RecommendRepository;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PopupService {

    private final PopupRepository popupRepository;
    private final PopupImageRepository popupImageRepository;
    private final RecommendRepository recommendRepository;
    private final PopupRecommendRepository popupRecommendRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final PopupTotalViewCountRepository popupTotalViewCountRepository;
    private final UserRecommendRepository userRecommendRepository;
    private final UsersRepository usersRepository;
    private final PopupResponseDtoMapper popupResponseDtoMapper;
    private final ObjectMapper objectMapper;

    public List<PopupResponseDto> getAllPopupList() {
        List<Popup> popupList = popupRepository.findAll();

        return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
    }

    @Transactional(readOnly = true)
    public List<PopupResponseDto> getSearchPopupList(String q) {
        String term = (q == null ? "" : q.trim());
        if (term.isEmpty()) return List.of();

        List<Popup> popupList = popupRepository.searchActivatedByKeyword(term);
        if (popupList.isEmpty()) return List.of();

        return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
    }

    public List<PopupResponseDto> getUpcomingPopupList(Integer upcomingDays) {
        int days = (upcomingDays == null || upcomingDays <= 0) ? 10 : upcomingDays;

        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusDays(days);

        List<Popup> popupList = popupRepository.findByActivatedTrueAndStartDateBetween(startDate, endDate);

        return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
    }

    public List<PopupResponseDto> getInProgressPopupList() {
        List<Popup> popupList = popupRepository.findInProgressPopupList();

        return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
    }

    @Transactional(readOnly = true)
    public List<RegionDistrictsResponse> getRegionDistricts() {
        List<PopupRepository.RegionDistrictsRaw> rawList = popupRepository.findRegionDistrictsJson();
        List<RegionDistrictsResponse> regionDistrictsResponseList = new ArrayList<>();

        for (PopupRepository.RegionDistrictsRaw regionDistrictsRaw : rawList) {
            try {
                List<String> districts = objectMapper.readValue(
                        regionDistrictsRaw.getDistricts(),
                        new TypeReference<List<String>>() {
                        }
                );

                RegionDistrictsResponse regionDistrictsResponse = RegionDistrictsResponse.builder()
                        .region(regionDistrictsRaw.getRegion())
                        .districtList(districts)
                        .build();

                regionDistrictsResponseList.add(regionDistrictsResponse);
            } catch (Exception e) {
                throw new RuntimeException("지역/구 JSON 파싱 오류: " + regionDistrictsRaw.getDistricts(), e);
            }
        }

        return regionDistrictsResponseList;
    }

    @Transactional(readOnly = true)
    public List<PopupResponseDto> getFilteredPopupList(String region, String district, SortStandard sortStandard, Double latitude, Double longitude) {
        String normalizedDistrict = StringNormalizer.normalizeDistrict(district);

        if (sortStandard == SortStandard.LIKES) {
            // region + district + 좋아요 수 기준 정렬
            List<Popup> popupList = popupRepository.findPopupListByRegionAndLikes(region, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else {
            // region + district + 위.경도로 가까운 순 정렬
            List<Popup> popupList = popupRepository.findPopupListByRegionAndDistance(region, normalizedDistrict, latitude, longitude);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        }

    }

    @Transactional(readOnly = true)
    public PopupResponseDto getPopupByUuid(String popupUuid) {
        Popup popup = popupRepository.findByUuid(popupUuid)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다. "));

        // 팝업 이미지
        List<String> imageUrlList = popupImageRepository
                .findAllByPopup_IdOrderByPopup_IdAscSortOrderAsc(popup.getId())
                .stream()
                .map(PopupImage::getImageUrl)
                .toList();

        // 추천
        List<String> recommendNameList = popupRecommendRepository.findAllByPopup_Id(popup.getId())
                .stream()
                .map(r -> r.getRecommend().getRecommendName())
                .toList();

        //좋아요 수
        Long favoriteCount = userFavoriteRepository.countByPopupUuid(popup.getUuid());

        // 조회 수
        Long viewCount = popupTotalViewCountRepository.getViewCountByPopupUuid(popup.getUuid());

        // DTO 조립
        PopupResponseDto popupResponseDto = PopupResponseDto.builder()
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
                .imageUrlList(imageUrlList)
                .mediaType(popup.getMediaType())
                .recommendList(recommendNameList)
                .favoriteCount(favoriteCount)
                .viewCount(viewCount)
                .build();

        return popupResponseDto;
    }

    public List<PopupResponseDto> getFilteredHomePopupList(String region, String district, HomeSortStandard homeSortStandard) {
        String normalizedRegion = StringNormalizer.normalizeRegion(region);
        String normalizedDistrict = StringNormalizer.normalizeDistrict(district);

        if (homeSortStandard == HomeSortStandard.NEWEST) {
            List<Popup> popupList = popupRepository.findActiveByNewest(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else if (homeSortStandard == HomeSortStandard.CLOSING_SOON) {
            List<Popup> popupList = popupRepository.findActiveByClosingSoon(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else if (homeSortStandard == HomeSortStandard.MOST_FAVORITED) {
            List<Popup> popupList = popupRepository.findActiveByMostFavorited(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else if (homeSortStandard == HomeSortStandard.MOST_VIEWED) {
            List<Popup> popupList = popupRepository.findActiveByMostViewed(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else {
            throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다: " + homeSortStandard);
        }
    }

    public List<PopupResponseDto> getFilteredMapPopupList(String region, String district, Double latitude, Double longitude, MapSortStandard mapSortStandard) {
        String normalizedRegion = StringNormalizer.normalizeRegion(region);
        String normalizedDistrict = StringNormalizer.normalizeDistrict(district);

        if (mapSortStandard == MapSortStandard.CLOSEST) {
            List<Popup> popupList = popupRepository.findActiveByClosest(normalizedRegion, normalizedDistrict, latitude, longitude);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else if (mapSortStandard == MapSortStandard.NEWEST) {
            List<Popup> popupList = popupRepository.findActiveByNewest(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else if (mapSortStandard == MapSortStandard.CLOSING_SOON) {
            List<Popup> popupList = popupRepository.findActiveByClosingSoon(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else if (mapSortStandard == MapSortStandard.MOST_FAVORITED) {
            List<Popup> popupList = popupRepository.findActiveByMostFavorited(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else if (mapSortStandard == MapSortStandard.MOST_VIEWED) {
            List<Popup> popupList = popupRepository.findActiveByMostViewed(normalizedRegion, normalizedDistrict);

            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        } else {
            throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다.: " + mapSortStandard);
        }

    }

    @Transactional
    public void registerPopup(PopupRegisterRequestDto popupRegisterRequestDto) {

        // popup 테이블 저장
        Popup popup = Popup.builder()
                .name(popupRegisterRequestDto.getName())
                .startDate(popupRegisterRequestDto.getStartDate())
                .endDate(popupRegisterRequestDto.getEndDate())
                .openTime(popupRegisterRequestDto.getOpenTime())
                .closeTime(popupRegisterRequestDto.getCloseTime())
                .address(popupRegisterRequestDto.getAddress())
                .roadAddress(popupRegisterRequestDto.getRoadAddress())
                .longitude(popupRegisterRequestDto.getLongitude())
                .latitude(popupRegisterRequestDto.getLatitude())
                .region(popupRegisterRequestDto.getRegion())
                .geocodingQuery(popupRegisterRequestDto.getGeocodingQuery())
                .instaPostId(popupRegisterRequestDto.getInstaPostId())
                .instaPostUrl(popupRegisterRequestDto.getInstaPostUrl())
                .captionSummary(popupRegisterRequestDto.getCaptionSummary())
                .caption(popupRegisterRequestDto.getCaption())
                .mediaType(popupRegisterRequestDto.getMediaType() != null ? MediaType.valueOf(popupRegisterRequestDto.getMediaType()) : null)
                .activated(Boolean.TRUE.equals(popupRegisterRequestDto.getIsActive()))
                .build();
        popupRepository.save(popup);

        // popup 이미지 저장
        if (popupRegisterRequestDto.getImageList() != null && !popupRegisterRequestDto.getImageList().isEmpty()) {
            List<PopupImage> imageList = new ArrayList<>();
            for (int i = 0; i < popupRegisterRequestDto.getImageList().size(); i++) {
                PopupImageUpsertRequestDto image = popupRegisterRequestDto.getImageList().get(i);
                imageList.add(PopupImage.builder()
                        .popup(popup)
                        .imageUrl(image.getImageUrl())
                        .sortOrder(image.getSortOrder() != null ? image.getSortOrder() : i)
                        .build());
            }
            popupImageRepository.saveAll(imageList);
        }

        // popup 이미지 저장
        if (popupRegisterRequestDto.getRecommendIdList() != null && !popupRegisterRequestDto.getRecommendIdList().isEmpty()) {
            List<Recommend> found = recommendRepository.findAllById(popupRegisterRequestDto.getRecommendIdList());
            if (found.size() != popupRegisterRequestDto.getRecommendIdList().size()) {
                throw new IllegalArgumentException("유효하지 않은 recommendId가 포함되어 있습니다. ");
            }

            List<PopupRecommend> popupRecommendList = new ArrayList<>();
            for (Recommend recommend : found) {
                popupRecommendList.add(PopupRecommend.builder()
                        .popup(popup)
                        .recommend(recommend)
                        .build());
            }
            popupRecommendRepository.saveAll(popupRecommendList);
        }
    }

    @Transactional(readOnly = true)
    public List<PopupResponseDto> getRecommendPopupList(String userUuid) {
        Users user = usersRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        List<UserRecommend> userRecommendList = userRecommendRepository.findAllByUser_Uuid(userUuid);

        Set<Long> pickedPopupIdSetList = new HashSet<>();
        List<Popup> popupList = new ArrayList<>(10);

        for (UserRecommend userRecommend : userRecommendList) {
            Long recommendId = userRecommend.getRecommend().getId();

            List<Popup> matchedPopupList = popupRecommendRepository.findActivePopupsByRecommendId(recommendId, PageRequest.of(0, 2));

            for (Popup popup : matchedPopupList) {
                if (pickedPopupIdSetList.add(popup.getId())) { // 중복 제거
                    popupList.add(popup);
                    if (popupList.size() == 10) {
                        break; // 10개 채우면 바로 종료
                    }
                }
            }

            if (popupList.size() == 10) {
                break;
            }
        }

        if (popupList.size() < 10) {
            int remain = 10 - popupList.size();

            List<Long> excludeIds = new ArrayList<>(pickedPopupIdSetList); // 이미 뽑은 것 제외
            List<Popup> randomPopups = popupRepository.findRandomActivePopupsExcluding(
                    excludeIds,
                    excludeIds.size(),
                    remain
            );

            for (Popup popup : randomPopups) {
                if (pickedPopupIdSetList.add(popup.getId())) {
                    popupList.add(popup);
                }
            }
        }

        return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
    }

    @Transactional(readOnly = true)
    public List<PopupResponseDto> getRelatedPopupList(String popupUuid) {

        Popup popup = popupRepository.findByUuid(popupUuid)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다. "));

        PopupRecommend popupRecommend = popupRecommendRepository.findByPopupId(popup.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 팝업에는 추천 값이 존재하지 않습니다. "));//entty 말고 id로 조회하는 것부터 시작

        Long recommendId = popupRecommend.getRecommend().getId();

        List<Popup> relatedPopupList = popupRecommendRepository.findRelatedActivePopupList(recommendId);
        relatedPopupList.removeIf(p -> p.getId().equals(popup.getId()));

        List<Popup> popupList = relatedPopupList.stream()
                .distinct()
                .limit(10)
                .toList();

        if (popupList.size() == 10) {
            return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
        }

        int remain = 10 - popupList.size();
        List<Long> excludeIds = popupList.stream() // 이미 뽑은 것 제외
                .map(Popup::getId)
                .toList();

        List<Popup> randomPopups = popupRepository.findRandomActivePopupsExcluding(
                excludeIds,
                excludeIds.size(),
                remain
        );

        List<Popup> finalPopupList = new ArrayList<>(10);
        finalPopupList.addAll(popupList);
        finalPopupList.addAll(randomPopups);

        return popupResponseDtoMapper.toPopupResponseDtoList(finalPopupList);
    }

    @Transactional(readOnly = true)
    public List<PopupResponseDto> getRandomPopupList() {

        List<Popup> popupList = popupRepository.findRandomActivePopups();

        return popupResponseDtoMapper.toPopupResponseDtoList(popupList);
    }

}
