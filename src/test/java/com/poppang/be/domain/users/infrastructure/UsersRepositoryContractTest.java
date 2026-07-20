package com.poppang.be.domain.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.poppang.be.domain.users.entity.Provider;
import com.poppang.be.domain.users.entity.SignupStatus;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

class UsersRepositoryContractTest {

  @Test
  void supportsProviderIdentityActiveLookupAndUuidWriteLock() throws Exception {
    Method socialIdentity =
        UsersRepository.class.getMethod("findByProviderAndUid", Provider.class, String.class);
    Method activeUser =
        UsersRepository.class.getMethod(
            "findByUuidAndDeletedFalseAndSignupStatus", String.class, SignupStatus.class);
    Method writeLock = UsersRepository.class.getMethod("findByUuidForUpdate", String.class);

    assertThat(socialIdentity.getReturnType()).isEqualTo(Optional.class);
    assertThat(activeUser.getReturnType()).isEqualTo(Optional.class);
    assertThat(writeLock.getReturnType()).isEqualTo(Optional.class);
    assertThat(writeLock.getAnnotation(Lock.class).value())
        .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    assertThat(writeLock.getAnnotation(Query.class).value()).contains("Users").contains("uuid");
  }

  @Test
  void legacyIdentityLookupsRemainAvailableForV1() {
    assertThat(repositoryMethodNames())
        .contains("findByUid", "findByUuid", "findByUuidAndDeletedFalse");
  }

  private java.util.List<String> repositoryMethodNames() {
    return Arrays.stream(UsersRepository.class.getMethods()).map(Method::getName).toList();
  }
}
