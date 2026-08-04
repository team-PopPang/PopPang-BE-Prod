package com.poppang.be.domain.popup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.entity.UserFavorite;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.v2.V2UserPopupResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupRecommend;
import com.poppang.be.domain.popup.enums.HomeSortStandard;
import com.poppang.be.domain.popup.enums.MapSortStandard;
import com.poppang.be.domain.popup.infrastructure.PopupAdvertisementRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.mapper.V2UserPopupResponseDtoMapper;
import com.poppang.be.domain.recommend.entity.Recommend;
import com.poppang.be.domain.recommend.entity.UserRecommend;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class V2UserPopupAdvancedServiceImplTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private PopupRepository popupRepository;
  @Mock private PopupAdvertisementRepository popupAdvertisementRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private UsersRepository usersRepository;
  @Mock private V2UserPopupResponseDtoMapper popupResponseDtoMapper;
  @Mock private PopupRecommendRepository popupRecommendRepository;
  @Mock private UserRecommendRepository userRecommendRepository;
  @Mock private PopupHomeFilterService popupHomeFilterService;

  @InjectMocks private V2UserPopupServiceImpl popupService;

  @Test
  void homeFilterUsesPrincipalUserFavoritesAndLegacyFilterService() {
    Popup popup = popup(1L);
    V2UserPopupResponseDto response = mock(V2UserPopupResponseDto.class);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID))
        .willReturn(List.of(new UserFavorite(user(), popup)));
    given(popupHomeFilterService.getFilteredPopupList("서울", "성동구", HomeSortStandard.NEWEST))
        .willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup), Set.of(1L)))
        .willReturn(List.of(response));

    assertThat(
            popupService.getFilteredHomePopupList(USER_UUID, "서울", "성동구", HomeSortStandard.NEWEST))
        .containsExactly(response);
  }

  @ParameterizedTest
  @EnumSource(MapSortStandard.class)
  void mapFilterNormalizesRegionAndDistrictAndUsesTheLegacySortRepository(
      MapSortStandard sortStandard) {
    Popup popup = popup(1L);
    V2UserPopupResponseDto response = mock(V2UserPopupResponseDto.class);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup), Set.of()))
        .willReturn(List.of(response));
    stubMapRepository(sortStandard, popup);

    assertThat(
            popupService.getFilteredMapPopupList(
                USER_UUID, "서울특별시", "성동구", 37.5, 127.0, sortStandard))
        .containsExactly(response);

    verifyMapRepository(sortStandard);
  }

  @Test
  void mapFilterRejectsMissingSortStandardAfterValidatingThePrincipalUser() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());

    assertThatThrownBy(
            () -> popupService.getFilteredMapPopupList(USER_UUID, "서울", "전체", null, null, null))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SORT_STANDARD));
  }

  @Test
  void principalRecommendDeduplicatesMatchesFillsRandomAndKeepsFavoriteState() {
    Popup first = popup(1L);
    Popup second = popup(2L);
    Popup third = popup(3L);
    Popup random = popup(4L);
    Recommend firstRecommend = recommend(10L);
    Recommend secondRecommend = recommend(20L);
    V2UserPopupResponseDto response = mock(V2UserPopupResponseDto.class);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userRecommendRepository.findAllByUser_Uuid(USER_UUID))
        .willReturn(
            List.of(
                new UserRecommend(user(), firstRecommend),
                new UserRecommend(user(), secondRecommend)));
    given(popupRecommendRepository.findActivePopupsByRecommendId(any(), any(Pageable.class)))
        .willAnswer(
            invocation ->
                invocation.<Long>getArgument(0).equals(10L)
                    ? List.of(first, second)
                    : List.of(second, third));
    given(popupRepository.findRandomActivePopupsExcluding(List.of(1L, 2L, 3L), 3, 7))
        .willReturn(List.of(random));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID))
        .willReturn(List.of(new UserFavorite(user(), second)));
    given(popupAdvertisementRepository.findActiveAdvertisements(any(), any()))
        .willReturn(List.of());
    given(
            popupResponseDtoMapper.toResponseDtoList(
                List.of(first, second, third, random), Set.of(2L)))
        .willReturn(List.of(response));

    assertThat(popupService.getRecommendPopupList(USER_UUID)).containsExactly(response);

    verify(popupRepository).findRandomActivePopupsExcluding(List.of(1L, 2L, 3L), 3, 7);
  }

  @Test
  void relatedPopupKeepsLegacyRecommendAndRandomFillFlow() {
    Popup current = popup(1L);
    Popup relatedOne = popup(2L);
    Popup relatedTwo = popup(3L);
    Popup random = popup(4L);
    Recommend recommend = recommend(10L);
    V2UserPopupResponseDto response = mock(V2UserPopupResponseDto.class);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(current));
    given(popupRecommendRepository.findByPopupId(1L))
        .willReturn(
            Optional.of(PopupRecommend.builder().popup(current).recommend(recommend).build()));
    given(popupRecommendRepository.findRelatedActivePopupList(10L))
        .willReturn(
            new java.util.ArrayList<>(List.of(current, relatedOne, relatedOne, relatedTwo)));
    given(popupRepository.findRandomActivePopupsExcluding(List.of(2L, 3L), 2, 8))
        .willReturn(List.of(random));
    given(
            popupResponseDtoMapper.toResponseDtoList(
                List.of(relatedOne, relatedTwo, random), Set.of()))
        .willReturn(List.of(response));

    assertThat(popupService.getRelatedPopupList(USER_UUID, POPUP_UUID)).containsExactly(response);
  }

  @Test
  void relatedPopupKeepsLegacyMissingRecommendError() {
    Popup current = popup(1L);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(current));
    given(popupRecommendRepository.findByPopupId(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> popupService.getRelatedPopupList(USER_UUID, POPUP_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.POPUP_RECOMMEND_NOT_FOUND));

    verifyNoInteractions(popupResponseDtoMapper);
  }

  @Test
  void recommendationCategoryUsesTargetIdAndPrincipalFavoriteState() {
    Popup popup = popup(1L);
    V2UserPopupResponseDto response = mock(V2UserPopupResponseDto.class);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID))
        .willReturn(List.of(new UserFavorite(user(), popup)));
    given(popupRepository.findActivePopupsByRecommendId(21L)).willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup), Set.of(1L)))
        .willReturn(List.of(response));

    assertThat(popupService.getRecommendationPopupList(USER_UUID, 21L)).containsExactly(response);
  }

  private void stubMapRepository(MapSortStandard sortStandard, Popup popup) {
    switch (sortStandard) {
      case CLOSEST -> given(popupRepository.findActiveByClosest("서울", "성동구", 37.5, 127.0))
          .willReturn(List.of(popup));
      case NEWEST -> given(popupRepository.findActiveByNewest("서울", "성동구"))
          .willReturn(List.of(popup));
      case CLOSING_SOON -> given(popupRepository.findActiveByClosingSoon("서울", "성동구"))
          .willReturn(List.of(popup));
      case MOST_FAVORITED -> given(popupRepository.findActiveByMostFavorited("서울", "성동구"))
          .willReturn(List.of(popup));
      case MOST_VIEWED -> given(popupRepository.findActiveByMostViewed("서울", "성동구"))
          .willReturn(List.of(popup));
    }
  }

  private void verifyMapRepository(MapSortStandard sortStandard) {
    switch (sortStandard) {
      case CLOSEST -> verify(popupRepository).findActiveByClosest("서울", "성동구", 37.5, 127.0);
      case NEWEST -> verify(popupRepository).findActiveByNewest("서울", "성동구");
      case CLOSING_SOON -> verify(popupRepository).findActiveByClosingSoon("서울", "성동구");
      case MOST_FAVORITED -> verify(popupRepository).findActiveByMostFavorited("서울", "성동구");
      case MOST_VIEWED -> verify(popupRepository).findActiveByMostViewed("서울", "성동구");
    }
  }

  private Users user() {
    return Users.builder().uuid(USER_UUID).build();
  }

  private Popup popup(Long id) {
    return Popup.builder().id(id).uuid(id == 1L ? POPUP_UUID : "popup-" + id).name("팝업").build();
  }

  private Recommend recommend(Long id) {
    Recommend recommend = mock(Recommend.class);
    given(recommend.getId()).willReturn(id);
    return recommend;
  }
}
