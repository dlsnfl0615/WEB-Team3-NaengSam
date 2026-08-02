package com.naengsam.quick.domain.upload.service;

/**
 * 파일 스토리지 추상화. 운영은 {@link S3Uploader}, 로컬 개발은 {@link DevUploader} 가 주입된다.
 */
public interface Uploader {

    /**
     * 클라이언트가 이 key로 직접 PUT 할 수 있는 presigned URL을 발급한다.
     */
    String generateUploadUrl(String key, String contentType);

    /**
     * 클라이언트가 이 key의 파일을 직접 GET 할 수 있는 presigned URL을 발급한다.
     */
    String generateDownloadUrl(String key);

    /**
     * 이 key로 실제 파일이 존재하는지(=업로드가 완료됐는지) 확인한다.
     */
    boolean exists(String key);
}
