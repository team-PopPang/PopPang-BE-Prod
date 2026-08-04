package com.poppang.be.domain.alert.application;

import com.poppang.be.domain.alert.dto.v2.V2WorkerUserAlertRegisterRequestDto;

public interface V2InternalUserAlertService {

  void registerUserAlert(String userUuid, V2WorkerUserAlertRegisterRequestDto request);
}
