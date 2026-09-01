package com.naengsam.quick.domain.upload.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 업로드 설정. {@code upload.*} (application.properties) 로 바인딩된다.
 */
@ConfigurationProperties(prefix = "upload")  // application.properties의 "upload.*" 키를 이 record 필드로 자동 바인딩 → annotations.md
public record UploadProperties(  // Java 14+ 불변 데이터 클래스. 생성자·getter·equals·hashCode·toString 자동 생성
        String bucketName
) {
}
