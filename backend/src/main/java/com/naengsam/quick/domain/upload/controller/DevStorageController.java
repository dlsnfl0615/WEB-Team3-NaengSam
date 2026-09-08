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
// DevUploader.devStorageUrl()이 만드는 URL이 정확히 이 경로("/api/v1/upload/dev-storage?key=...")를 가리킨다.
// 즉 여기가 클라이언트가 "presigned URL로 직접 PUT/GET한다"고 믿는, 실제로는 이 서버 자신인 목적지다.
@RequestMapping("/api/v1/upload/dev-storage")
// 운영(S3Uploader가 켜진 상태)에서는 이 컨트롤러 자체가 스프링 컨텍스트에 등록되지 않는다 — 로컬/개발 전용 엔드포인트라
// 실수로 운영에서 호출될 경로 자체가 존재하지 않게 막는 것. S3Uploader와 정확히 상호 배타적으로 켜진다.
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "false", matchIfMissing = true)
public class DevStorageController {

    private final InMemoryFileStore fileStore;  // DevUploader와 이 컨트롤러가 함께 바라보는 "가짜 S3 버킷"

    @PutMapping  // HTTP PUT 요청 처리. @GetMapping/@PostMapping/@DeleteMapping/@PatchMapping도 동일 구조
    // 실제 S3라면 클라이언트가 presigned URL로 이 PUT을 S3에 직접 보냈을 것. 여기서는 그 역할을 이 서버가 대신
    // 수행한다 — 즉 이 메서드 자체가 "S3에 파일을 올리는 동작"의 흉내다.
    public void put(@RequestParam String key, @RequestBody byte[] body,  // @RequestBody: HTTP 요청 body를 byte 배열로 직접 역직렬화
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {  // @RequestHeader: HTTP 헤더를 파라미터로 받음. required=false로 없어도 허용 → annotations.md
        // 실제 저장은 InMemoryFileStore로 위임한다 — 이 컨트롤러는 "S3 API 흉내"만 내고, 진짜 보관은 별도 컴포넌트가 맡는 역할 분리.
        fileStore.save(key, body, contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);  // MediaType.APPLICATION_OCTET_STREAM_VALUE: "application/octet-stream" 문자열 상수. Content-Type 미지정 시 기본값
    }

    @GetMapping
    // 실제 S3라면 클라이언트가 presigned GET URL로 S3에서 직접 파일을 받았을 것. 여기서는 이 서버가 그 응답을 대신 만들어준다.
    public ResponseEntity<byte[]> get(@RequestParam String key) {  // ResponseEntity: HTTP 상태코드·헤더·바디를 직접 제어할 때 사용. 순수 DTO를 반환하면 CommonResponseAdvice가 래핑하지만, 여기선 바이너리를 그대로 내려야 해서 직접 구성
        StoredFile storedFile = fileStore.find(key)
                .orElseThrow(() -> new BusinessException(UploadErrorCode.FILE_NOT_FOUND));  // Optional이 비어있으면 람다로 예외 생성

        return ResponseEntity.ok()  // HTTP 200 응답 빌더 시작
                .header(HttpHeaders.CONTENT_TYPE, storedFile.contentType())  // 응답 헤더 직접 설정
                .body(storedFile.bytes());  // 응답 body 설정 후 ResponseEntity 완성
    }
}
