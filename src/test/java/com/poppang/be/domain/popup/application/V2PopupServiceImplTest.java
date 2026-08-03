package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.v2.V2PopupResponseDto;
import com.poppang.be.domain.popup.dto.v2.V2RegionDistrictsResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.enums.SortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.mapper.V2PopupResponseDtoMapper;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class V2PopupServiceImplTest {

  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private PopupRepository popupRepository;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private UserRecommendRepository userRecommendRepository;
  @Mock private UsersRepository usersRepository;
  @Mock private PopupHomeFilterService popupHomeFilterService;
  @Mock private V2PopupResponseDtoMapper popupResponseDtoMapper;

  private V2PopupServiceImpl popupService;

  @BeforeEach
  void setUp() {
    popupService =
        new V2PopupServiceImpl(
            popupRepository,
            popupRecommendRepository,
            userRecommendRepository,
            usersRepository,
            popupHomeFilterService,
            popupResponseDtoMapper,
            new ObjectMapper());
  }

  @Test
  void listEndpointsKeepTheLegacyRepositoryAndMapperPaths() {
    Popup popup = popup();
    V2PopupResponseDto response = response();
    given(popupRepository.findAll()).willReturn(List.of(popup));
    given(popupRepository.findInProgressPopupList()).willReturn(List.of(popup));
    given(popupRepository.findRandomActivePopups()).willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup))).willReturn(List.of(response));

    assertThat(popupService.getAllPopupList()).containsExactly(response);
    assertThat(popupService.getInProgressPopupList()).containsExactly(response);
    assertThat(popupService.getRandomPopupList()).containsExactly(response);

    verify(popupRepository).findAll();
    verify(popupRepository).findInProgressPopupList();
    verify(popupRepository).findRandomActivePopups();
  }

  @Test
  void searchTrimsTheQueryAndUsesTheLegacyActivatedSearch() {
    Popup popup = popup();
    given(popupRepository.searchActivatedByKeyword("성수")).willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup))).willReturn(List.of(response()));

    assertThat(popupService.getSearchPopupList("  성수  ")).hasSize(1);

    verify(popupRepository).searchActivatedByKeyword("성수");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " \t "})
  void blankSearchKeepsTheLegacyEmptyResultWithoutRepositoryAccess(String query) {
    assertThat(popupService.getSearchPopupList(query)).isEmpty();
    verifyNoInteractions(popupRepository, popupResponseDtoMapper);
  }

  @Test
  void upcomingUsesTomorrowAndDefaultsNonPositiveDaysToTen() {
    LocalDate before = LocalDate.now().plusDays(1);
    given(
            popupRepository.findByActivatedTrueAndStartDateBetween(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .willReturn(List.of());
    given(popupResponseDtoMapper.toResponseDtoList(List.of())).willReturn(List.of());

    popupService.getUpcomingPopupList(0);

    ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(popupRepository)
        .findByActivatedTrueAndStartDateBetween(startCaptor.capture(), endCaptor.capture());
    LocalDate after = LocalDate.now().plusDays(1);
    assertThat(startCaptor.getValue()).isBetween(before, after);
    assertThat(endCaptor.getValue()).isEqualTo(startCaptor.getValue().plusDays(10));
  }

  @Test
  void detailValidatesTheTargetAndMapsTheLegacyPopup() {
    Popup popup = popup();
    V2PopupResponseDto response = response();
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(popupResponseDtoMapper.toDetailResponseDto(popup)).willReturn(response);

    assertThat(popupService.getPopupByUuid(POPUP_UUID)).isSameAs(response);
    verify(popupRepository).findByUuid(POPUP_UUID);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " \t "})
  void invalidPopupUuidReturnsNotFoundBeforeRepositoryAccess(String popupUuid) {
    assertThatThrownBy(() -> popupService.getPopupByUuid(popupUuid))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POPUP_NOT_FOUND));
    verifyNoInteractions(popupRepository, popupResponseDtoMapper);
  }

  @Test
  void missingPopupReturnsTheLegacyNotFoundError() {
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> popupService.getPopupByUuid(POPUP_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POPUP_NOT_FOUND));
    verifyNoInteractions(popupResponseDtoMapper);
  }

  @Test
  void regionDistrictsParsesTheLegacyJsonProjection() {
    PopupRepository.RegionDistrictsRaw row = mock(PopupRepository.RegionDistrictsRaw.class);
    given(row.getRegion()).willReturn("서울");
    given(row.getDistricts()).willReturn("[\"전체\",\"성동구\"]");
    given(popupRepository.findRegionDistrictsJson()).willReturn(List.of(row));

    List<V2RegionDistrictsResponseDto> result = popupService.getRegionDistricts();

    assertThat(result)
        .containsExactly(new V2RegionDistrictsResponseDto("서울", List.of("전체", "성동구")));
  }

  @Test
  void invalidRegionDistrictsJsonKeepsTheLegacyParseError() {
    PopupRepository.RegionDistrictsRaw row = mock(PopupRepository.RegionDistrictsRaw.class);
    given(row.getDistricts()).willReturn("not-json");
    given(popupRepository.findRegionDistrictsJson()).willReturn(List.of(row));

    assertThatThrownBy(() -> popupService.getRegionDistricts())
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.REGION_DISTRICTS_JSON_PARSE_ERROR));
  }

  @Test
  void generalFilterKeepsTheLegacyLikesAndDistancePathsAndDistrictNormalization() {
    Popup popup = popup();
    given(popupRepository.findPopupListByRegionAndLikes("서울", null)).willReturn(List.of(popup));
    given(popupRepository.findPopupListByRegionAndDistance("서울", "성동구", 37.5, 127.0))
        .willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup))).willReturn(List.of(response()));

    assertThat(popupService.getFilteredPopupList("서울", "전체", SortStandard.LIKES, null, null))
        .hasSize(1);
    assertThat(popupService.getFilteredPopupList("서울", " 성동 ", SortStandard.DISTANCE, 37.5, 127.0))
        .hasSize(1);

    verify(popupRepository).findPopupListByRegionAndLikes("서울", null);
    verify(popupRepository).findPopupListByRegionAndDistance("서울", "성동구", 37.5, 127.0);
  }

  @Test
  void homeFilterDelegatesToTheExistingSharedHelper() {
    Popup popup = popup();
    given(popupHomeFilterService.getFilteredPopupList("서울특별시", "전체", HomeSortStandard.NEWEST))
        .willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup))).willReturn(List.of(response()));

    assertThat(popupService.getFilteredHomePopupList("서울특별시", "전체", HomeSortStandard.NEWEST))
        .hasSize(1);
  }

  @Test
  void mapFilterKeepsEveryLegacySortPathAfterRegionAndDistrictNormalization() {
    given(popupResponseDtoMapper.toResponseDtoList(List.of())).willReturn(List.of());

    popupService.getFilteredMapPopupList("서울특별시", "성동", 37.5, 127.0, MapSortStandard.CLOSEST);
    popupService.getFilteredMapPopupList("서울특별시", "성동", null, null, MapSortStandard.NEWEST);
    popupService.getFilteredMapPopupList("서울특별시", "성동", null, null, MapSortStandard.CLOSING_SOON);
    popupService.getFilteredMapPopupList("서울특별시", "성동", null, null, MapSortStandard.MOST_FAVORITED);
    popupService.getFilteredMapPopupList("서울특별시", "성동", null, null, MapSortStandard.MOST_VIEWED);

    verify(popupRepository).findActiveByClosest("서울", "성동구", 37.5, 127.0);
    verify(popupRepository).findActiveByNewest("서울", "성동구");
    verify(popupRepository).findActiveByClosingSoon("서울", "성동구");
    verify(popupRepository).findActiveByMostFavorited("서울", "성동구");
    verify(popupRepository).findActiveByMostViewed("서울", "성동구");
  }

  @Test
  void mapFilterRejectsAnUnknownSortBeforeRepositoryAccess() {
    assertThatThrownBy(() -> popupService.getFilteredMapPopupList("서울", "전체", null, null, null))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SORT_STANDARD));
    verifyNoInteractions(popupRepository, popupResponseDtoMapper);
  }

  @Test
  void relatedRecommendationExcludesTheCurrentPopupWhenRandomlyFillingResults() {
    Popup current = popup(1L);
    Popup related = popup(2L);
    Popup random = popup(3L);
    Recommend recommend = mock(Recommend.class);
    PopupRecommend popupRecommend = mock(PopupRecommend.class);
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(current));
    given(popupRecommendRepository.findByPopupId(1L)).willReturn(Optional.of(popupRecommend));
    given(popupRecommend.getRecommend()).willReturn(recommend);
    given(recommend.getId()).willReturn(7L);
    given(popupRecommendRepository.findRelatedActivePopupList(7L))
        .willReturn(List.of(current, related, related));
    given(popupRepository.findRandomActivePopupsExcluding(List.of(1L, 2L), 2, 9))
        .willReturn(List.of(random));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(related, random)))
        .willReturn(List.of(response()));

    assertThat(popupService.getRelatedPopupList(POPUP_UUID)).hasSize(1);

    verify(popupRepository).findRandomActivePopupsExcluding(List.of(1L, 2L), 2, 9);
  }

  @Test
  void blankRelatedPopupUuidReturnsNotFoundBeforeRepositoryAccess() {
    assertThatThrownBy(() -> popupService.getRelatedPopupList(" "))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POPUP_NOT_FOUND));

    verifyNoInteractions(popupRepository, popupRecommendRepository, popupResponseDtoMapper);
  }

  @Test
  void relatedRecommendationKeepsLegacyMissingPopupAndRecommendErrors() {
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.empty());
    assertThatThrownBy(() -> popupService.getRelatedPopupList(POPUP_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POPUP_NOT_FOUND));

    Popup popup = popup();
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(popupRecommendRepository.findByPopupId(1L)).willReturn(Optional.empty());
    assertThatThrownBy(() -> popupService.getRelatedPopupList(POPUP_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.POPUP_RECOMMEND_NOT_FOUND));
  }

  @Test
  void categoryRecommendationUsesTheLegacyActiveCategoryQuery() {
    Popup popup = popup();
    given(popupRepository.findActivePopupsByRecommendId(7L)).willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup))).willReturn(List.of(response()));

    assertThat(popupService.getRecommendationPopupList(7L)).hasSize(1);

    verify(popupRepository).findActivePopupsByRecommendId(7L);
  }

  @Test
  void personalizedRecommendationUsesThePrincipalUserAndKeepsLegacyDeduplicationAndRandomFill() {
    String userUuid = "11111111-1111-1111-1111-111111111111";
    Users user = Users.builder().uuid(userUuid).build();
    UserRecommend firstPreference = mock(UserRecommend.class);
    UserRecommend secondPreference = mock(UserRecommend.class);
    Recommend firstRecommend = mock(Recommend.class);
    Recommend secondRecommend = mock(Recommend.class);
    Popup first = popup(1L);
    Popup duplicate = popup(2L);
    Popup third = popup(3L);
    Popup random = popup(4L);
    given(usersRepository.findByUuid(userUuid)).willReturn(Optional.of(user));
    given(userRecommendRepository.findAllByUser_Uuid(userUuid))
        .willReturn(List.of(firstPreference, secondPreference));
    given(firstPreference.getRecommend()).willReturn(firstRecommend);
    given(secondPreference.getRecommend()).willReturn(secondRecommend);
    given(firstRecommend.getId()).willReturn(10L);
    given(secondRecommend.getId()).willReturn(20L);
    given(popupRecommendRepository.findActivePopupsByRecommendId(10L, PageRequest.of(0, 2)))
        .willReturn(List.of(first, duplicate));
    given(popupRecommendRepository.findActivePopupsByRecommendId(20L, PageRequest.of(0, 2)))
        .willReturn(List.of(duplicate, third));
    given(
            popupRepository.findRandomActivePopupsExcluding(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(7)))
        .willReturn(List.of(first, random));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(first, duplicate, third, random)))
        .willReturn(List.of(response()));

    assertThat(popupService.getRecommendPopupList(userUuid)).hasSize(1);

    verify(usersRepository).findByUuid(userUuid);
    verify(userRecommendRepository).findAllByUser_Uuid(userUuid);
  }

  @Test
  void personalizedRecommendationKeepsTheLegacyMissingUserError() {
    String userUuid = "missing-user";
    given(usersRepository.findByUuid(userUuid)).willReturn(Optional.empty());

    assertThatThrownBy(() -> popupService.getRecommendPopupList(userUuid))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    verifyNoInteractions(userRecommendRepository, popupRecommendRepository);
  }

  private Popup popup() {
    return popup(1L);
  }

  private Popup popup(Long id) {
    return Popup.builder().id(id).uuid(POPUP_UUID + id).name("팝업").build();
  }

  private V2PopupResponseDto response() {
    return new V2PopupResponseDto(
        POPUP_UUID,
        "팝업",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        null,
        0,
        0);
  }
}
