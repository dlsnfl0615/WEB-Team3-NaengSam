package com.naengsam.quick.domain.upload.service;

import com.naengsam.quick.global.admin.InMemoryStateProbe;
import com.naengsam.quick.global.admin.InMemoryStructureDto;
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
public class InMemoryFileStore implements InMemoryStateProbe {  // 관리자 화면에서 인메모리 상태를 조회하기 위한 인터페이스 구현

    private final Map<String, StoredFile> filesByKey = new ConcurrentHashMap<>();  // 멀티스레드 안전한 HashMap. 동시 요청에서 race condition 방지 → java-patterns.md

    public void save(String key, byte[] bytes, String contentType) {
        filesByKey.put(key, new StoredFile(bytes, contentType));
    }

    public Optional<StoredFile> find(String key) {
        return Optional.ofNullable(filesByKey.get(key));  // null 가능 값을 Optional로 감쌈. 호출부에서 null 체크 대신 orElseThrow/orElse 사용
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
        long totalBytes = filesByKey.values().stream()  // 컬렉션을 스트림으로 변환
                .mapToLong(file -> file.bytes().length)  // 각 StoredFile을 바이트 크기(long)로 변환. mapToLong은 LongStream 반환 → java-patterns.md
                .sum();  // LongStream의 합산 단말 연산

        return List.of(InMemoryStructureDto.ofMap("filesByKey", "S3 key → 업로드된 파일 바이트 (제거 경로 없음)", filesByKey)  // Java 9+ 불변 List 생성 팩토리
                .withBreakdown(Map.of("총 바이트", totalBytes)));
    }

    public record StoredFile(byte[] bytes, String contentType) {  // 중첩 record. 파일 바이트+타입을 묶는 불변 데이터 클래스
    }
}
