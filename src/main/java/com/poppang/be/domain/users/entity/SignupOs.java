package com.poppang.be.domain.users.entity;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;

public enum SignupOs {
  IOS,
  ANDROID;

  public static SignupOs fromNullable(String value) {
    if (value == null) {
      return null;
    }
    try {
      return valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_SIGNUP_OS);
    }
  }
}
