package com.poppang.be.domain.favorite.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.favorite.dto.request.UserFavoriteDeleteRequestDto;
import com.poppang.be.domain.favorite.dto.request.UserFavoriteRegisterRequestDto;
import com.poppang.be.domain.favorite.dto.response.FavoriteCountResponseDto;
import com.poppang.be.domain.favorite.entity.UserFavorite;
import com.poppang.be.domain.favorite.infrastructure.UserFavoriteRepository;
import com.poppang.be.domain.popup.dto.app.response.PopupUserResponseDto;
import com.poppang.be.domain.popup.entity.Popup;
import com.poppang.be.domain.popup.infrastructure.PopupImageRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRecommendRepository;
import com.poppang.be.domain.popup.infrastructure.PopupRepository;
import com.poppang.be.domain.popup.infrastructure.PopupTotalViewCountRepository;
import com.poppang.be.domain.popup.mapper.PopupUserResponseDtoMapper;
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
public class UserFavoriteServiceImpl implements UserFavoriteService {

  private final UsersRepository usersRepository;
  private final PopupRepository popupRepository;
  private final UserFavoriteRepository userFavoriteRepository;
  private final PopupImageRepository popupImageRepository;
  private final PopupRecommendRepository popupRecommendRepository;
  private final PopupTotalViewCountRepository popupTotalViewCountRepository;
  private final PopupUserResponseDtoMapper popupUserResponseDtoMapper;

  @Override
  @Transactional
  public void registerFavorite(UserFavoriteRegisterRequestDto userFavoriteRegisterRequestDto) {
    Users user =
        usersRepository
            .findByUuid(userFavoriteRegisterRequestDto.getUserUuid())
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Popup popup =
        popupRepository
            .findByUuid(userFavoriteRegisterRequestDto.getPopupUuid())
            .orElseThrow(() -> new BaseException(ErrorCode.POPUP_NOT_FOUND));

    boolean exists = userFavoriteRepository.existsByUserAndPopup(user, popup);
    if (exists) {
      throw new BaseException(ErrorCode.FAVORITE_ALREADY_EXISTS);
    }

    UserFavorite userFavorite = new UserFavorite(user, popup);

    userFavoriteRepository.save(userFavorite);
  }

  @Override
  @Transactional
  public void deleteFavorite(UserFavoriteDeleteRequestDto userFavoriteDeleteRequestDto) {
    UserFavorite userFavorite =
        userFavoriteRepository
            .findByUserUuidAndPopupUuid(
                userFavoriteDeleteRequestDto.getUserUuid(),
                userFavoriteDeleteRequestDto.getPopupUuid())
            .orElseThrow(() -> new BaseException(ErrorCode.FAVORITE_NOT_FOUND));

    userFavoriteRepository.delete(userFavorite);
  }

  @Override
  @Transactional(readOnly = true)
  public FavoriteCountResponseDto getFavoriteCount(String popupUuid) {
    long count = userFavoriteRepository.countByPopupUuid(popupUuid);

    return FavoriteCountResponseDto.from(count);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PopupUserResponseDto> getFavoritePopupList(String userUuid) {
    Users user =
        usersRepository
            .findByUuid(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    List<UserFavorite> userFavoriteList =
        userFavoriteRepository.findAllActivatedByUserUuid(userUuid);
    if (userFavoriteList.isEmpty()) {
      return List.of();
    }

    Set<Long> favoritedPopupIdList =
        userFavoriteList.stream().map(f -> f.getPopup().getId()).collect(Collectors.toSet());

    List<Popup> popupList = popupRepository.findAllById(favoritedPopupIdList);

    return popupUserResponseDtoMapper.toPopupUserResponseDtoList(popupList, favoritedPopupIdList);
  }
}
