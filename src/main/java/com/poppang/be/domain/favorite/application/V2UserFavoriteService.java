package com.poppang.be.domain.favorite.application;

import com.poppang.be.domain.favorite.dto.v2.V2FavoriteCountResponseDto;
import com.poppang.be.domain.favorite.dto.v2.V2FavoritePopupResponseDto;
import java.util.List;

public interface V2UserFavoriteService {

  void registerFavorite(String userUuid, String popupUuid);

  void deleteFavorite(String userUuid, String popupUuid);

  V2FavoriteCountResponseDto getFavoriteCount(String popupUuid);

  List<V2FavoritePopupResponseDto> getFavoritePopupList(String userUuid);
}
