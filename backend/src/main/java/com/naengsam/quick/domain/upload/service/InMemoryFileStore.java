package com.naengsam.quick.domain.upload.service;

import com.naengsam.quick.global.debug.InMemoryStateProbe;
import com.naengsam.quick.global.debug.InMemoryStructureDto;
import java.util.List;
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
public class InMemoryFileStore implements InMemoryStateProbe {

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

    /**
     * 업로드된 파일이 힙에 얼마나 쌓였는지. 이 저장소에는 제거 경로가 아예 없어 프로세스가 살아 있는 동안 계속 누적되므로, 개수보다 총 바이트가 실질적인 위험 지표다. S3를 쓰는 환경
     * ({@code upload.s3-enabled=true})에서는 이 빈 자체가 등록되지 않아 목록에도 나타나지 않는다.
     */
    @Override
    public List<InMemoryStructureDto> inMemoryStructures() {
        long totalBytes = filesByKey.values().stream()
                .mapToLong(file -> file.bytes().length)
                .sum();

        return List.of(InMemoryStructureDto.ofMap("filesByKey", "S3 key → 업로드된 파일 바이트 (제거 경로 없음)", filesByKey)
                .withBreakdown(Map.of("총 바이트", totalBytes)));
    }

    public record StoredFile(byte[] bytes, String contentType) {
    }
}
