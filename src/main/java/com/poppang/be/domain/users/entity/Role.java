package com.poppang.be.domain.users.entity;

public enum Role {
  ADMIN,
  MEMBER;

  public String toAuthority() {
    return "ROLE_" + this.name();
  }
}
