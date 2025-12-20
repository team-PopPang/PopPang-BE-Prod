package com.poppang.be.common.exception;

import com.poppang.be.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // 커스텀 예외
  @ExceptionHandler(BaseException.class)
  public ResponseEntity<ApiResponse<Void>> handleBase(BaseException e) {
    ErrorCode ec = e.getErrorCode();

    // 보통 비즈니스 예외는 warn/info (운영 정책에 따라)
    log.warn("Business exception: code={}, message={}", ec.getCode(), e.getMessage());

    return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.error(ec, e.getMessage()));
  }

  // 예상 못한 모든 예외
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {

    // 예상 못한 건 error + stacktrace 필수
    log.error("Unexpected exception", e);

    ErrorCode ec = ErrorCode.INTERNAL_ERROR;
    return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.error(ec));
  }
}
