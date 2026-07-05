package com.poppang.be.domain.popup.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record PopupSubmissionImageStorageProperties(
    String submissionImageRoot, String submissionImageUrlPrefix) {}
