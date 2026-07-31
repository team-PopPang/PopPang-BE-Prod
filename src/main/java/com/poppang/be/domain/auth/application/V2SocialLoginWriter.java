package com.poppang.be.domain.auth.application;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
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
class V2SocialLoginWriter {

  private final UsersRepository usersRepository;
  private final V2SocialSignupTokenService signupTokenService;

  @Transactional
  V2SocialLoginResult login(Provider expectedProvider, VerifiedSocialIdentity identity) {
    requireProvider(expectedProvider, identity);
    return usersRepository
        .findByProviderAndUid(expectedProvider, identity.uid())
        .map(user -> lockAndResolve(expectedProvider, user.getUuid(), identity))
        .orElseGet(() -> createPendingUser(expectedProvider, identity));
  }

  @Transactional
  V2SocialLoginResult recoverAfterCreateCollision(
      Provider expectedProvider, VerifiedSocialIdentity identity) {
    requireProvider(expectedProvider, identity);
    Users user =
        usersRepository
            .findByProviderAndUid(expectedProvider, identity.uid())
            .orElseThrow(() -> new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT));
    return lockAndResolve(expectedProvider, user.getUuid(), identity);
  }

  private V2SocialLoginResult createPendingUser(
      Provider expectedProvider, VerifiedSocialIdentity identity) {
    if (usersRepository.existsByUidAndProviderNot(identity.uid(), expectedProvider)) {
      throw new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
    }

    Users created =
        usersRepository.saveAndFlush(
            Users.builder()
                .uid(identity.uid())
                .provider(expectedProvider)
                .email(identity.verifiedEmail())
                .role(Role.MEMBER)
                .signupStatus(SignupStatus.PENDING)
                .build());
    return lockAndResolve(expectedProvider, created.getUuid(), identity);
  }

  private V2SocialLoginResult lockAndResolve(
      Provider expectedProvider, String userUuid, VerifiedSocialIdentity identity) {
    Users user =
        usersRepository
            .findByUuidForUpdate(userUuid)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE));
    if (user.isDeleted()) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    if (user.getProvider() != expectedProvider || !identity.uid().equals(user.getUid())) {
      throw new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
    }

    user.updateVerifiedEmail(identity.verifiedEmail());
    if (user.getSignupStatus() == SignupStatus.COMPLETED) {
      return V2SocialLoginResult.completed(V2AuthUserResponseDto.from(user));
    }
    if (user.getSignupStatus() != SignupStatus.PENDING) {
      throw new BaseException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    return V2SocialLoginResult.pending(signupTokenService.issue(user.getUuid()));
  }

  private void requireProvider(Provider expectedProvider, VerifiedSocialIdentity identity) {
    if (expectedProvider == null || identity == null || identity.provider() != expectedProvider) {
      throw new BaseException(ErrorCode.SOCIAL_IDENTITY_CONFLICT);
    }
  }
}
