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
  ALERT_KEYWORD_ALREADY_EXISTS(HttpStatus.CONFLICT, 4004, "이미 등록된 알림 키워드입니다."),

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
  SIGNUP_ALREADY_COMPLETED(HttpStatus.CONFLICT, 4204, "이미 회원가입을 완료한 사용자입니다."),
  INVALID_USER_REQUEST(HttpStatus.BAD_REQUEST, 4205, "사용자 요청값이 올바르지 않습니다."),

  // ==================================================
  // 4300 ~ 4399 : Popup (팝업)
  // ==================================================
  POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, 4301, "팝업을 찾을 수 없습니다."),
  REGION_DISTRICTS_JSON_PARSE_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, 4302, "지역/구 정보 파싱 중 오류가 발생했습니다."),
  INVALID_SORT_STANDARD(HttpStatus.BAD_REQUEST, 4303, "지원하지 않는 정렬 기준입니다."),
  INVALID_RECOMMEND_ID(HttpStatus.BAD_REQUEST, 4304, "유효하지 않은 recommendId가 포함되어 있습니다."),
  POPUP_RECOMMEND_NOT_FOUND(HttpStatus.NOT_FOUND, 4305, "해당 팝업에는 추천 값이 존재하지 않습니다."),
  INVALID_SUBMITTER_USER_UUID(HttpStatus.BAD_REQUEST, 4306, "제보자 UUID는 필수입니다."),
  INVALID_POPUP_SUBMISSION_REQUEST(HttpStatus.BAD_REQUEST, 4307, "팝업 제보 요청값이 올바르지 않습니다."),
  INVALID_ADMIN_USER_UUID(HttpStatus.BAD_REQUEST, 4308, "관리자 UUID는 필수입니다."),
  INVALID_POPUP_SUBMISSION_STATUS(HttpStatus.BAD_REQUEST, 4309, "지원하지 않는 제보 상태입니다."),
  POPUP_SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, 4310, "팝업 제보를 찾을 수 없습니다."),
  INVALID_POPUP_SUBMISSION_UPDATE_STATUS(
      HttpStatus.BAD_REQUEST, 4311, "처리할 제보 상태는 APPROVED 또는 REJECTED만 가능합니다."),
  POPUP_SUBMISSION_ALREADY_PROCESSED(HttpStatus.CONFLICT, 4312, "이미 처리된 팝업 제보입니다."),
  INVALID_POPUP_SEARCH_QUERY(HttpStatus.BAD_REQUEST, 4313, "검색어는 필수입니다."),
  REGION_REQUIRED_FOR_DISTRICT(HttpStatus.BAD_REQUEST, 4314, "구를 조회하려면 지역이 필요합니다."),

  // ==================================================
  // 5000 ~ 5099 : Auth / JWT (인증)
  // ==================================================
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 5001, "유효하지 않은 토큰입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, 5002, "만료된 토큰입니다."),
  UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, 5003, "지원하지 않는 토큰 형식입니다."),
  MALFORMED_TOKEN(HttpStatus.UNAUTHORIZED, 5004, "손상된 토큰입니다."),
  TOKEN_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, 5005, "토큰 서명이 올바르지 않습니다."),
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, 5006, "인증이 필요합니다."),
  REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, 5007, "최신 Refresh Token이 아닙니다."),
  INSUFFICIENT_AUTHORITY(HttpStatus.FORBIDDEN, 5008, "요청을 처리할 권한이 없습니다."),
  INVALID_WORKER_API_KEY(HttpStatus.UNAUTHORIZED, 5009, "Worker API Key가 올바르지 않습니다."),
  ACCOUNT_NOT_ACTIVE(HttpStatus.UNAUTHORIZED, 5010, "활성 상태의 계정이 아닙니다."),
  INVALID_REFRESH_REQUEST(HttpStatus.BAD_REQUEST, 5011, "Refresh Token 요청이 올바르지 않습니다."),
  SIGNUP_PROVIDER_MISMATCH(HttpStatus.FORBIDDEN, 5012, "회원가입 provider가 일치하지 않습니다."),
  AUTH_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, 5013, "인증 저장소를 사용할 수 없습니다."),
  SIGNUP_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, 5014, "최신 Signup Token이 아닙니다."),
  INVALID_SOCIAL_CREDENTIAL(HttpStatus.UNAUTHORIZED, 5015, "소셜 로그인 정보가 올바르지 않습니다."),
  SOCIAL_IDENTITY_CONFLICT(HttpStatus.CONFLICT, 5016, "소셜 계정 식별자가 충돌했습니다."),
  INVALID_SIGNUP_REQUEST(HttpStatus.BAD_REQUEST, 5017, "회원가입 요청값이 올바르지 않습니다."),
  RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 5018, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

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
