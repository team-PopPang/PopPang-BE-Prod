package com.poppang.be.common.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.poppang.be.common.exception.ErrorCode;
import lombok.Getter;

@JsonPropertyOrder({"success", "code", "message", "data"})
@Getter
public class ApiResponse<T> {

  private static final int SUCCESS_CODE = 0;
  private static final String SUCCESS_MESSAGE = "요청 성공!";

  private final boolean success;
  private final int code;
  private final String message;
  private final T data;

  private ApiResponse(boolean success, int code, String message, T data) {
    this.success = success;
    this.code = code;
    this.message = message;
    this.data = data;
  }

  // 성공 (기본)
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, SUCCESS_CODE, SUCCESS_MESSAGE, data);
  }

  // 성공 (커스텀 메시지)
  public static <T> ApiResponse<T> ok(String message, T data) {
    return new ApiResponse<>(true, SUCCESS_CODE, message, data);
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode) {
    return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
  }

  public static <T> ApiResponse<T> error(ErrorCode errorCode, String overrideMessage) {
    return new ApiResponse<>(false, errorCode.getCode(), overrideMessage, null);
  }
}
