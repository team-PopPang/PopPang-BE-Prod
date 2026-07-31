package com.poppang.be.domain.auth.application;

record V2SocialSignupToken(String compactToken, long expiresIn) {}
