package com.poppang.be.domain.popup.application;

import org.springframework.web.multipart.MultipartFile;

/**
 * 제보 이미지 바이트 저장소. URL 경로와 저장 key 규칙(yyyy/MM/uuid.확장자)은 {@link PopupSubmissionImageStorage}가 정하고,
 * 구현체는 URL prefix 아래 상대 경로만 받는다.
 */
public interface PopupSubmissionImageStore {

  /** 상대 경로에 이미지를 저장한다. 실패하면 BaseException을 던진다. */
  void save(String relativePath, MultipartFile image, String contentType);

  /** 상대 경로의 이미지를 삭제한다. 실패해도 예외를 던지지 않는다. */
  void delete(String relativePath);
}
