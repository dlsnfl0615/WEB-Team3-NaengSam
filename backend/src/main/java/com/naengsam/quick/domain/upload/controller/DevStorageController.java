package com.naengsam.quick.domain.upload.controller;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.InMemoryFileStore;
import com.naengsam.quick.domain.upload.service.InMemoryFileStore.StoredFile;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.session.PublicApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link com.naengsam.quick.domain.upload.service.DevUploader}가 발급한 URL이 실제로 가리키는 대상.
 * 로컬 개발에서 S3 presigned PUT/GET을 흉내낸다. 실제 presigned URL과 마찬가지로 로그인 세션 없이 접근한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload/dev-storage")
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "false", matchIfMissing = true)
@PublicApi
public class DevStorageController {

    private final InMemoryFileStore fileStore;

    @PutMapping
    public void put(@RequestParam String key, @RequestBody byte[] body,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {
        fileStore.save(key, body, contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@RequestParam String key) {
        StoredFile storedFile = fileStore.find(key)
                .orElseThrow(() -> new BusinessException(UploadErrorCode.FILE_NOT_FOUND));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, storedFile.contentType())
                .body(storedFile.bytes());
    }
}
