package com.naengsam.quick.domain.upload.service;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 로컬/개발용 업로더. {@link InMemoryFileStore}를 실제 S3처럼 다뤄, 클라이언트가 발급받은 URL로 진짜
 * PUT/GET을 해볼 수 있게 한다({@link com.naengsam.quick.domain.upload.controller.DevStorageController} 참고).
 * {@code upload.s3-enabled} 가 없거나 false 일 때 활성화된다(자격증명 불필요).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "false", matchIfMissing = true)  // matchIfMissing=true: 프로퍼티가 아예 없을 때도 이 빈 등록(로컬 기본값) → annotations.md
public class DevUploader implements Uploader {

    private final InMemoryFileStore fileStore;

    @Override
    public String generateUploadUrl(String key, String contentType) {
        log.info("[DEV-UPLOAD] key={} contentType={}", key, contentType);
        return devStorageUrl(key);
    }

    @Override
    public String generateDownloadUrl(String key) {
        log.info("[DEV-DOWNLOAD] key={}", key);
        return devStorageUrl(key);
    }

    @Override
    public boolean exists(String key) {
        return fileStore.exists(key);
    }

    private String devStorageUrl(String key) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();  // 현재 요청 컨텍스트에서 scheme+host+port 추출(예: http://localhost:8080) → java-patterns.md
        return baseUrl + "/api/v1/upload/dev-storage?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);  // URL 쿼리 파라미터의 특수문자를 %XX 형식으로 인코딩 → java-patterns.md
    }
}
