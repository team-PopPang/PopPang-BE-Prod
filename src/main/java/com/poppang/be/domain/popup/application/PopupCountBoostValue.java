package com.poppang.be.domain.popup.application;

public record PopupCountBoostValue(long viewCountBoost, long favoriteCountBoost) {

  public static final PopupCountBoostValue ZERO = new PopupCountBoostValue(0L, 0L);
}
