package com.naengsam.quick.domain.upload.dto;

/**
 * presigned URL 발급 응답. {@code url}로 클라이언트가 S3에 직접 PUT하고, {@code key}는 이후 업로드 확인/등록 요청에 그대로 사용한다.
 */
public record PresignedUrlResponseDto(  // Java 14+ 불변 DTO. 생성자·getter(url()/key())·equals·hashCode·toString 자동 생성. class로 만들면 필요한 보일러플레이트를 record가 대신함
        String url,
        String key
) {
}
