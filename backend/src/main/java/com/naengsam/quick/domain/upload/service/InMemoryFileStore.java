package com.naengsam.quick.domain.upload.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link DevUploader}와 {@link com.naengsam.quick.domain.upload.controller.DevStorageController}가 공유하는
 * 인메모리 파일 저장소. 로컬 개발에서 실제 S3 없이 presigned PUT/GET을 흉내내기 위해 사용한다.
 */
@Component
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryFileStore {

    private final Map<String, StoredFile> filesByKey = new ConcurrentHashMap<>();

    public void save(String key, byte[] bytes, String contentType) {
        filesByKey.put(key, new StoredFile(bytes, contentType));
    }

    public Optional<StoredFile> find(String key) {
        return Optional.ofNullable(filesByKey.get(key));
    }

    public boolean exists(String key) {
        return filesByKey.containsKey(key);
    }

    public record StoredFile(byte[] bytes, String contentType) {
    }
}
