package com.naengsam.quick.domain.upload.controller;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.InMemoryFileStore;
import com.naengsam.quick.domain.upload.service.InMemoryFileStore.StoredFile;
import com.naengsam.quick.global.exception.BusinessException;
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
 * 로컬 개발에서 S3 presigned PUT/GET을 흉내낸다. 실제 presigned URL과 달리 서명 검증이 없으므로
 * 로그인 세션을 요구한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/upload/dev-storage")
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "false", matchIfMissing = true)
public class DevStorageController {

    private final InMemoryFileStore fileStore;

    @PutMapping  // HTTP PUT 요청 처리. @GetMapping/@PostMapping/@DeleteMapping/@PatchMapping도 동일 구조
    public void put(@RequestParam String key, @RequestBody byte[] body,  // @RequestBody: HTTP 요청 body를 byte 배열로 직접 역직렬화
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {  // @RequestHeader: HTTP 헤더를 파라미터로 받음. required=false로 없어도 허용 → annotations.md
        fileStore.save(key, body, contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);  // MediaType.APPLICATION_OCTET_STREAM_VALUE: "application/octet-stream" 문자열 상수. Content-Type 미지정 시 기본값
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@RequestParam String key) {  // ResponseEntity: HTTP 상태코드·헤더·바디를 직접 제어할 때 사용. 순수 DTO를 반환하면 CommonResponseAdvice가 래핑하지만, 여기선 바이너리를 그대로 내려야 해서 직접 구성
        StoredFile storedFile = fileStore.find(key)
                .orElseThrow(() -> new BusinessException(UploadErrorCode.FILE_NOT_FOUND));  // Optional이 비어있으면 람다로 예외 생성

        return ResponseEntity.ok()  // HTTP 200 응답 빌더 시작
                .header(HttpHeaders.CONTENT_TYPE, storedFile.contentType())  // 응답 헤더 직접 설정
                .body(storedFile.bytes());  // 응답 body 설정 후 ResponseEntity 완성
    }
}
