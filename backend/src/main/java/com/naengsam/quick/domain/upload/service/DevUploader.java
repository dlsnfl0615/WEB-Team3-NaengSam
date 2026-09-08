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
// S3Uploader와 정확히 반대 조건(false 또는 미설정)에서 켜진다 — Uploader 인터페이스 하나에 이 둘이 서로
// 배타적으로 꽂히므로, 호출부(S3PresignService)는 지금 로컬인지 운영인지 전혀 몰라도 된다.
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "false", matchIfMissing = true)  // matchIfMissing=true: 프로퍼티가 아예 없을 때도 이 빈 등록(로컬 기본값) → annotations.md
public class DevUploader implements Uploader {

    private final InMemoryFileStore fileStore;

    @Override
    // S3Uploader라면 여기서 AWS에 진짜 서명(presign)을 요청했겠지만, 이 구현은 서명을 아예 만들지 않는다.
    // 대신 "이 서버 자신의 dev-storage 엔드포인트"를 가리키는 URL을 돌려줘서, 클라이언트 입장에서는
    // presigned URL을 받은 것과 동일하게 동작하도록 흉내만 낸다.
    public String generateUploadUrl(String key, String contentType) {
        log.info("[DEV-UPLOAD] key={} contentType={}", key, contentType);
        return devStorageUrl(key);
    }

    @Override
    // 업로드와 마찬가지로, 진짜 S3 GET presigned URL 대신 dev-storage 조회 엔드포인트 URL을 돌려준다.
    public String generateDownloadUrl(String key) {
        log.info("[DEV-DOWNLOAD] key={}", key);
        return devStorageUrl(key);
    }

    @Override
    // S3Uploader.exists()는 S3에 HeadObject를 날리지만, 여기서는 같은 질문을 인메모리 저장소에 그대로 위임한다 —
    // "업로드가 실제로 완료됐는가"라는 역할은 동일하게 수행하되 뒷단만 S3 대신 힙 메모리로 바뀐 것.
    public boolean exists(String key) {
        return fileStore.exists(key);
    }

    // DevStorageController가 실제로 처리하게 될 그 URL을 여기서 조립한다. 즉 이 메서드가 만드는 문자열이
    // "가짜 presigned URL"의 전체 모습이다 — 서명 파라미터 없이 key만 쿼리로 붙는다는 점이 진짜 S3 presigned
    // URL과의 결정적 차이(그래서 컨트롤러 쪽이 별도로 로그인 세션을 요구해 서명 검증 부재를 보완한다).
    private String devStorageUrl(String key) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();  // 현재 요청 컨텍스트에서 scheme+host+port 추출(예: http://localhost:8080) → java-patterns.md
        return baseUrl + "/api/v1/upload/dev-storage?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);  // URL 쿼리 파라미터의 특수문자를 %XX 형식으로 인코딩 → java-patterns.md
    }
}
