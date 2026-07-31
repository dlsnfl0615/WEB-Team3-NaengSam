package com.naengsam.quick.domain.upload.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 업로드 설정. {@code upload.*} (application.properties) 로 바인딩된다.
 */
@ConfigurationProperties(prefix = "upload")
public record UploadProperties(
        String bucketName
) {
}
