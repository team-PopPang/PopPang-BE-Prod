package com.poppang.be.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // ==================================================
  // 4000 ~ 4099 : UserAlert (알림)
  // ==================================================
  USER_ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, 4001, "해당 팝업에 대한 알림 이력이 존재하지 않습니다."),
  USER_ALERT_ALREADY_EXISTS(HttpStatus.CONFLICT, 4002, "이미 해당 팝업에 대한 알림 기록이 존재합니다."),
  ALERT_KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, 4003, "해당 키워드가 존재하지 않습니다."),

  // ==================================================
  // 4100 ~ 4199 : Favorite (찜)
  // ==================================================
  FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, 4101, "이미 찜한 팝업입니다."),
  FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, 4102, "해당 찜 기록이 존재하지 않습니다."),

  // ==================================================
  // 4200 ~ 4299 : Users (유저)
  // ==================================================
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, 4201, "유저를 찾을 수 없습니다."),
  DUPLICATE_NICKNAME(HttpStatus.CONFLICT, 4202, "이미 존재하는 닉네임입니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, 4203, "관리자만 사용할 수 있는 기능입니다."),

  // ==================================================
  // 4300 ~ 4399 : Popup (팝업)
  // ==================================================
  POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, 4301, "팝업을 찾을 수 없습니다."),
  REGION_DISTRICTS_JSON_PARSE_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, 4302, "지역/구 정보 파싱 중 오류가 발생했습니다."),
  INVALID_SORT_STANDARD(HttpStatus.BAD_REQUEST, 4303, "지원하지 않는 정렬 기준입니다."),
  INVALID_RECOMMEND_ID(HttpStatus.BAD_REQUEST, 4304, "유효하지 않은 recommendId가 포함되어 있습니다."),
  POPUP_RECOMMEND_NOT_FOUND(HttpStatus.NOT_FOUND, 4305, "해당 팝업에는 추천 값이 존재하지 않습니다."),

  // ==================================================
  // 6000 ~ 6999 : System / Unexpected
  // ==================================================
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 6000, "서버 에러가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final int code;
  private final String message;

  ErrorCode(HttpStatus httpStatus, int code, String message) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.message = message;
  }
}
