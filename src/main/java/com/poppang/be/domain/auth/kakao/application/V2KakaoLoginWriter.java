package com.poppang.be.domain.auth.kakao.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.auth.application.VerifiedSocialIdentity;
import com.poppang.be.domain.auth.dto.v2.response.V2AuthUserResponseDto;
import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.Role;
import com.poppang.be.domain.users.entity.SignupStatus;
import com.poppang.be.domain.users.entity.Users;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class V2KakaoLoginWriter {

  private final UsersRepository usersRepository;
  private final V2SignupTokenService signupTokenService;

  @Transactional
  V2KakaoLoginResult login(VerifiedSocialIdentity identity) {
    requireKakao(identity);
    return usersRepository
        .findByProviderAndUid(Provider.KAKAO, identity.uid())
        .map(user -> lockAndResolve(user.getUuid(), identity))
        .orElseGet(() -> createPendingUser(identity));
  }

  @Transactional
  V2KakaoLoginResult recoverAfterCreateCollision(VerifiedSocialIdentity identity) {
    requireKakao(identity);
    Users user =
        usersRepository
            .findByProviderAndUid(Provider.KAKAO, identity.uid())
            .orElseThrow(() -> new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT));
    return lockAndResolve(user.getUuid(), identity);
  }

  private V2KakaoLoginResult createPendingUser(VerifiedSocialIdentity identity) {
    if (usersRepository.existsByUidAndProviderNot(identity.uid(), Provider.KAKAO)) {
      throw new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
    }

    Users created =
        usersRepository.saveAndFlush(
            Users.builder()
                .uid(identity.uid())
                .provider(Provider.KAKAO)
                .email(identity.verifiedEmail())
                .role(Role.MEMBER)
                .signupStatus(SignupStatus.PENDING)
                .build());
    return lockAndResolve(created.getUuid(), identity);
  }

  private V2KakaoLoginResult lockAndResolve(String userUuid, VerifiedSocialIdentity identity) {
    Users user =
        usersRepository
            .findByUuidForUpdate(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE));
    if (user.isDeleted()) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    if (user.getProvider() != Provider.KAKAO || !identity.uid().equals(user.getUid())) {
      throw new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
    }

    user.updateVerifiedEmail(identity.verifiedEmail());
    if (user.getSignupStatus() == SignupStatus.COMPLETED) {
      return V2KakaoLoginResult.completed(V2AuthUserResponseDto.from(user));
    }
    if (user.getSignupStatus() != SignupStatus.PENDING) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    return V2KakaoLoginResult.pending(signupTokenService.issue(user.getUuid()));
  }

  private void requireKakao(VerifiedSocialIdentity identity) {
    if (identity == null || identity.provider() != Provider.KAKAO) {
      throw new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
    }
  }
}
