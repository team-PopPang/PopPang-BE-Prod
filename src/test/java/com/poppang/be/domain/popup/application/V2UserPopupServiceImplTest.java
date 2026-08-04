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
import com.poppang.be.domain.popup.dto.v2.V2UserPopupScrollResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.entity.PopupAdvertisement;
import com.poppang.be.domain.popup.entity.PopupAdvertisementPlacement;
import com.poppang.be.domain.popup.infrastructure.PopupAdvertisementRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.mapper.V2UserPopupResponseDtoMapper;
import com.poppang.be.domain.recommend.infrastructure.UserRecommendRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class V2UserPopupServiceImplTest {

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

  private V2UserPopupServiceImpl popupService;

  @BeforeEach
  void setUp() {
    popupService =
        new V2UserPopupServiceImpl(
            popupRepository,
            popupAdvertisementRepository,
            userFavoriteRepository,
            usersRepository,
            popupResponseDtoMapper,
            popupRecommendRepository,
            userRecommendRepository,
            popupHomeFilterService);
  }

  @Test
  void listEndpointsKeepTheLegacyRepositoriesAndFavoritePersonalization() {
    Popup popup = popup(1L);
    V2UserPopupResponseDto response = response();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID))
        .willReturn(List.of(new UserFavorite(user(), popup)));
    given(popupRepository.findAll()).willReturn(List.of(popup));
    given(popupRepository.findInProgressPopupList()).willReturn(List.of(popup));
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup), Set.of(1L)))
        .willReturn(List.of(response));

    assertThat(popupService.getAllPopupList(USER_UUID)).containsExactly(response);
    assertThat(popupService.getInProgressPopupList(USER_UUID)).containsExactly(response);

    verify(popupRepository).findAll();
    verify(popupRepository).findInProgressPopupList();
  }

  @Test
  void detailValidatesTheUserAndMapsFavoriteStateWithPrincipalUuid() {
    Popup popup = popup(1L);
    V2UserPopupResponseDto response = response();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(popupResponseDtoMapper.toDetailResponseDto(popup, USER_UUID)).willReturn(response);

    assertThat(popupService.getPopupByUuid(USER_UUID, POPUP_UUID)).isSameAs(response);

    verify(usersRepository).findByUuid(USER_UUID);
    verify(popupRepository).findByUuid(POPUP_UUID);
    verify(popupResponseDtoMapper).toDetailResponseDto(popup, USER_UUID);
  }

  @Test
  void missingPopupKeepsTheLegacyNotFoundError() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> popupService.getPopupByUuid(USER_UUID, POPUP_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POPUP_NOT_FOUND));
    verifyNoInteractions(popupResponseDtoMapper);
  }

  @Test
  void upcomingUsesTomorrowAndDefaultsNonPositiveDaysToTen() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(popupRepository.findByActivatedTrueAndStartDateBetween(any(), any()))
        .willReturn(List.of());
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());
    given(popupResponseDtoMapper.toResponseDtoList(List.of(), Set.of())).willReturn(List.of());
    LocalDate before = LocalDate.now().plusDays(1);

    popupService.getUpcomingPopupList(USER_UUID, 0);

    ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
    verify(popupRepository)
        .findByActivatedTrueAndStartDateBetween(startCaptor.capture(), endCaptor.capture());
    LocalDate after = LocalDate.now().plusDays(1);
    assertThat(startCaptor.getValue()).isBetween(before, after);
    assertThat(endCaptor.getValue()).isEqualTo(startCaptor.getValue().plusDays(10));
  }

  @Test
  void searchTrimsTheQueryAndUsesTheLegacyActivatedSearch() {
    Popup popup = popup(1L);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(popupRepository.searchActivatedByKeyword("성수")).willReturn(List.of(popup));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());
    given(popupResponseDtoMapper.toResponseDtoList(List.of(popup), Set.of()))
        .willReturn(List.of(response()));

    assertThat(popupService.getSearchPopupList(USER_UUID, "  성수  ")).hasSize(1);

    verify(popupRepository).searchActivatedByKeyword("성수");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " \t "})
  void blankSearchChecksTheUserThenReturnsEmptyWithoutLoadingPopupData(String query) {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));

    assertThat(popupService.getSearchPopupList(USER_UUID, query)).isEmpty();

    verify(usersRepository).findByUuid(USER_UUID);
    verifyNoInteractions(popupRepository, userFavoriteRepository, popupResponseDtoMapper);
  }

  @Test
  void randomPrependsActiveAdvertisementsInPriorityOrderAndRemovesDuplicates() {
    Popup advertisementPopup = popup(1L);
    Popup randomPopup = popup(2L);
    PopupAdvertisement firstAdvertisement = mock(PopupAdvertisement.class);
    PopupAdvertisement duplicateAdvertisement = mock(PopupAdvertisement.class);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());
    given(popupRepository.findRandomActivePopups())
        .willReturn(List.of(advertisementPopup, randomPopup));
    given(
            popupAdvertisementRepository.findActiveAdvertisements(
                org.mockito.ArgumentMatchers.eq(PopupAdvertisementPlacement.USER_RECOMMEND_TOP),
                any(LocalDateTime.class)))
        .willReturn(List.of(firstAdvertisement, duplicateAdvertisement));
    given(firstAdvertisement.getPopupId()).willReturn(1L);
    given(duplicateAdvertisement.getPopupId()).willReturn(1L);
    given(popupRepository.findActiveInProgressByIdIn(List.of(1L)))
        .willReturn(List.of(advertisementPopup));
    given(
            popupResponseDtoMapper.toResponseDtoList(
                List.of(advertisementPopup, randomPopup), Set.of()))
        .willReturn(List.of(response()));

    assertThat(popupService.getRandomPopupList(USER_UUID)).hasSize(1);

    verify(popupResponseDtoMapper)
        .toResponseDtoList(List.of(advertisementPopup, randomPopup), Set.of());
  }

  @Test
  void unknownUserStopsBeforePopupFavoriteAndAdvertisementAccess() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> popupService.getRandomPopupList(USER_UUID))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

    verifyNoInteractions(
        popupRepository,
        popupAdvertisementRepository,
        userFavoriteRepository,
        popupResponseDtoMapper);
  }

  @Test
  void scrollFirstPageUsesTheLegacyPageSizeAndDescendingRepository() {
    Popup first = popup(30L);
    Popup second = popup(20L);
    V2UserPopupScrollResponseDto response = new V2UserPopupScrollResponseDto(List.of(), 20L, true);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(
            popupRepository.findByActivatedTrueAndEndDateGreaterThanEqualOrderByIdDesc(
                any(LocalDate.class), any(Pageable.class)))
        .willReturn(new SliceImpl<>(List.of(first, second), PageRequest.of(0, 15), true));
    given(popupResponseDtoMapper.toScrollResponseDto(List.of(first, second), USER_UUID, true))
        .willReturn(response);

    assertThat(popupService.getScrollPopupList(USER_UUID, null)).isSameAs(response);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(popupRepository)
        .findByActivatedTrueAndEndDateGreaterThanEqualOrderByIdDesc(
            any(LocalDate.class), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
    verify(popupResponseDtoMapper).toScrollResponseDto(List.of(first, second), USER_UUID, true);
  }

  @Test
  void scrollNextPageUsesIdLessThanCursorAndKeepsLastPageState() {
    Popup popup = popup(10L);
    V2UserPopupScrollResponseDto response =
        new V2UserPopupScrollResponseDto(List.of(), null, false);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user()));
    given(
            popupRepository.findByActivatedTrueAndEndDateGreaterThanEqualAndIdLessThanOrderByIdDesc(
                any(LocalDate.class), org.mockito.ArgumentMatchers.eq(20L), any(Pageable.class)))
        .willReturn(new SliceImpl<>(List.of(popup), PageRequest.of(0, 15), false));
    given(popupResponseDtoMapper.toScrollResponseDto(List.of(popup), USER_UUID, false))
        .willReturn(response);

    assertThat(popupService.getScrollPopupList(USER_UUID, 20L)).isSameAs(response);

    verify(popupRepository)
        .findByActivatedTrueAndEndDateGreaterThanEqualAndIdLessThanOrderByIdDesc(
            any(LocalDate.class), org.mockito.ArgumentMatchers.eq(20L), any(Pageable.class));
    verify(popupResponseDtoMapper).toScrollResponseDto(List.of(popup), USER_UUID, false);
  }

  private Users user() {
    return Users.builder().uuid(USER_UUID).build();
  }

  private Popup popup(Long id) {
    return Popup.builder().id(id).uuid(id == 1L ? POPUP_UUID : POPUP_UUID + id).name("팝업").build();
  }

  private V2UserPopupResponseDto response() {
    return new V2UserPopupResponseDto(
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
        0,
        false);
  }
}
