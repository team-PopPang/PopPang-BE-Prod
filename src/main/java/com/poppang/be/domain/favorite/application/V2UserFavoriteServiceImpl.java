package com.poppang.be.domain.favorite.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.dto.v2.V2FavoriteCountResponseDto;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import com.poppang.be.domain.favorite.entity.UserFavorite;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.application.PopupCountBoostService;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class V2UserFavoriteServiceImpl implements V2UserFavoriteService {

  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupCountBoostService popupCountBoostService;
  private final V2FavoritePopupResponseDtoMapper responseMapper;

  @Override
  @Transactional
  public void registerFavorite(String userUuid, String popupUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    Popup popup =
        popupRepository
            .findByUuid(popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    if (userFavoriteRepository.existsByUserAndPopup(user, popup)) {
      throw new BaseException(ErrorCode.FAVORITE_ALREADY_EXISTS);
    }

    userFavoriteRepository.save(new UserFavorite(user, popup));
  }

  @Override
  @Transactional
  public void deleteFavorite(String userUuid, String popupUuid) {
    UserFavorite favorite =
        userFavoriteRepository
            .findByUserUuidAndPopupUuid(userUuid, popupUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.FAVORITE_NOT_FOUND));

    userFavoriteRepository.delete(favorite);
  }

  @Override
  @Transactional(readOnly = true)
  public V2FavoriteCountResponseDto getFavoriteCount(String popupUuid) {
    long count =
        userFavoriteRepository.countByPopupUuid(popupUuid)
            + popupCountBoostService.getFavoriteCountBoostByPopupUuid(popupUuid);
    return new V2FavoriteCountResponseDto(count);
  }

  @Override
  @Transactional(readOnly = true)
  public List<V2FavoritePopupResponseDto> getFavoritePopupList(String userUuid) {
    usersRepository
        .findByUuid(userUuid)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<UserFavorite> favorites = userFavoriteRepository.findAllActivatedByUserUuid(userUuid);
    if (favorites.isEmpty()) {
      return List.of();
    }

    Set<Long> favoritePopupIds =
        favorites.stream().map(favorite -> favorite.getPopup().getId()).collect(Collectors.toSet());
    List<Popup> popups = popupRepository.findAllById(favoritePopupIds);

    return responseMapper.toResponseList(popups, favoritePopupIds);
  }
}
