package com.poppang.be.domain.favorite.application;

import com.poppang.be.domain.favorite.dto.request.UserFavoriteDeleteRequestDto;
import com.poppang.be.domain.favorite.dto.request.UserFavoriteRegisterRequestDto;
import com.poppang.be.domain.favorite.dto.response.FavoriteCountResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupUserResponseDto;
import java.util.List;

public interface UserFavoriteService {

  void registerFavorite(UserFavoriteRegisterRequestDto userFavoriteRegisterRequestDto);

  void deleteFavorite(UserFavoriteDeleteRequestDto userFavoriteDeleteRequestDto);

  FavoriteCountResponseDto getFavoriteCount(String popupUuid);

  List<PopupUserResponseDto> getFavoritePopupList(String userUuid);
}
