package com.poppang.be.domain.users.event;

import com.poppang.be.domain.users.entity.Users;

public record UserSignupCompletedEvent(Users user) {}
