package com.poppang.be.domain.favorite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.favorite.entity.UserFavorite;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V2UserFavoriteServiceTest {

  private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String POPUP_UUID = "22222222-2222-2222-2222-222222222222";

  @Mock private UsersRepository usersRepository;
  @Mock private PopupRepository popupRepository;
  @Mock private UserFavoriteRepository userFavoriteRepository;
  @Mock private PopupCountBoostService popupCountBoostService;
  @Mock private V2FavoritePopupResponseDtoMapper responseMapper;

  private V2UserFavoriteServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new V2UserFavoriteServiceImpl(
            usersRepository,
            popupRepository,
            userFavoriteRepository,
            popupCountBoostService,
            responseMapper);
  }

  @Test
  void registerFavoriteUsesOnlyThePrincipalUserAndPopupTarget() {
    Users user = Users.builder().uuid(USER_UUID).build();
    Popup popup = popup(1L, POPUP_UUID);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(userFavoriteRepository.existsByUserAndPopup(user, popup)).willReturn(false);

    service.registerFavorite(USER_UUID, POPUP_UUID);

    ArgumentCaptor<UserFavorite> favorite = ArgumentCaptor.forClass(UserFavorite.class);
    verify(userFavoriteRepository).save(favorite.capture());
    assertThat(favorite.getValue().getUser()).isSameAs(user);
    assertThat(favorite.getValue().getPopup()).isSameAs(popup);
  }

  @Test
  void registerFavoriteKeepsTheLegacyNotFoundAndDuplicateErrors() {
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.empty());
    assertError(() -> service.registerFavorite(USER_UUID, POPUP_UUID), ErrorCode.USER_NOT_FOUND);

    Users user = Users.builder().uuid(USER_UUID).build();
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.empty());
    assertError(() -> service.registerFavorite(USER_UUID, POPUP_UUID), ErrorCode.POPUP_NOT_FOUND);

    Popup popup = popup(1L, POPUP_UUID);
    given(popupRepository.findByUuid(POPUP_UUID)).willReturn(Optional.of(popup));
    given(userFavoriteRepository.existsByUserAndPopup(user, popup)).willReturn(true);
    assertError(
        () -> service.registerFavorite(USER_UUID, POPUP_UUID), ErrorCode.FAVORITE_ALREADY_EXISTS);

    verify(userFavoriteRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deleteFavoriteUsesPrincipalUserUuidAndPopupTarget() {
    UserFavorite favorite =
        new UserFavorite(Users.builder().uuid(USER_UUID).build(), popup(1L, POPUP_UUID));
    given(userFavoriteRepository.findByUserUuidAndPopupUuid(USER_UUID, POPUP_UUID))
        .willReturn(Optional.of(favorite));

    service.deleteFavorite(USER_UUID, POPUP_UUID);

    verify(userFavoriteRepository).delete(favorite);
  }

  @Test
  void deleteFavoriteKeepsTheLegacyNotFoundError() {
    given(userFavoriteRepository.findByUserUuidAndPopupUuid(USER_UUID, POPUP_UUID))
        .willReturn(Optional.empty());

    assertError(() -> service.deleteFavorite(USER_UUID, POPUP_UUID), ErrorCode.FAVORITE_NOT_FOUND);
  }

  @Test
  void favoriteCountIncludesTheExistingBoostValue() {
    given(userFavoriteRepository.countByPopupUuid(POPUP_UUID)).willReturn(7L);
    given(popupCountBoostService.getFavoriteCountBoostByPopupUuid(POPUP_UUID)).willReturn(3L);

    assertThat(service.getFavoriteCount(POPUP_UUID).count()).isEqualTo(10L);
  }

  @Test
  void favoritePopupListUsesOnlyThePrincipalUserAndV2Mapper() {
    Users user = Users.builder().uuid(USER_UUID).build();
    Popup popup = popup(5L, POPUP_UUID);
    UserFavorite favorite = new UserFavorite(user, popup);
    V2FavoritePopupResponseDto response =
        new V2FavoritePopupResponseDto(
            POPUP_UUID,
            "팝업",
            null,
            null,
            null,
            null,
            "주소",
            null,
            "서울",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            1,
            2,
            true);
    given(usersRepository.findByUuid(USER_UUID)).willReturn(Optional.of(user));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID))
        .willReturn(List.of(favorite));
    given(popupRepository.findAllById(Set.of(5L))).willReturn(List.of(popup));
    given(responseMapper.toResponseList(List.of(popup), Set.of(5L))).willReturn(List.of(response));

    assertThat(service.getFavoritePopupList(USER_UUID)).containsExactly(response);
    verify(userFavoriteRepository).findAllActivatedByUserUuid(USER_UUID);
    verify(responseMapper).toResponseList(List.of(popup), Set.of(5L));
  }

  @Test
  void favoritePopupListReturnsEmptyWithoutRunningTheMapper() {
    given(usersRepository.findByUuid(USER_UUID))
        .willReturn(Optional.of(Users.builder().uuid(USER_UUID).build()));
    given(userFavoriteRepository.findAllActivatedByUserUuid(USER_UUID)).willReturn(List.of());

    assertThat(service.getFavoritePopupList(USER_UUID)).isEmpty();
    verify(responseMapper, never())
        .toResponseList(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anySet());
  }

  private Popup popup(Long id, String uuid) {
    return Popup.builder().id(id).uuid(uuid).activated(true).build();
  }

  private void assertError(Runnable operation, ErrorCode expected) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            BaseException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
  }
}
