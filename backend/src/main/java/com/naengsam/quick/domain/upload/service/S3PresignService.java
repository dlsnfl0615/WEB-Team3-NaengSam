package com.naengsam.quick.domain.upload.service;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * S3 presigned URL 발급 및 실제 업로드 여부 확인을 담당한다. 파일 바이트 자체는 서버를 거치지 않고 클라이언트가 발급받은 URL로 S3에 직접 PUT/GET 한다. 실제 스토리지 호출은
 * {@link Uploader}(운영: {@link S3Uploader}, 로컬: {@link DevUploader})에 위임한다. key 발급/용도·소유자 검증은
 * {@link UploadSessionService}가 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3PresignService {

    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(  // Java 9+ 불변 Map 생성 팩토리. 선언 후 put() 불가 → java-patterns.md
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp"
    );

    private final Uploader uploader;  // 인터페이스 타입으로 주입. 운영=S3Uploader, 로컬=DevUploader가 @ConditionalOnProperty에 따라 자동 선택

    /**
     * 클라이언트가 이 key로 S3에 직접 PUT 할 수 있는 presigned URL을 발급한다.
     */
    public String generateUploadUrl(String key) {
        return uploader.generateUploadUrl(key, resolveContentType(key));
    }

    /**
     * 클라이언트가 이 key의 파일을 S3에서 직접 GET 할 수 있는 presigned URL을 발급한다. key가 실제로 존재하지 않으면 예외를 던진다.
     */
    public String generateDownloadUrl(String key) {
        if (!isFileUploaded(key)) {
            throw new BusinessException(UploadErrorCode.FILE_NOT_FOUND);
        }
        return uploader.generateDownloadUrl(key);
    }

    /**
     * 이 key로 실제 파일이 존재하는지(=클라이언트가 presigned URL로 업로드를 완료했는지) 확인한다.
     */
    public boolean isFileUploaded(String key) {
        return uploader.exists(key);
    }

    /**
     * key로 다운로드 URL 발급을 시도하되, 실패해도 예외를 던지지 않고 null로 degrade한다. 업로드 시점에 이미 존재를
     * 검증한 파일이 조회 시점엔 없을 수 있고(보존 정책 삭제, 개발 환경 인메모리 스토리지 재시작 등), 존재 확인
     * 자체가 스토리지 장애로 실패할 수도 있다({@link #generateDownloadUrl}이 둘 다 BusinessException으로 던짐).
     * 이때 전체 요청을 실패시키면 안 되고 "사진 없음"처럼 조회 실패를 그냥 보여주면 되는 화면(완료 요약, 관리자
     * 심사 목록 등)에서 사용한다. 업로드 여부를 검증해서 실제로 막아야 하는 곳({@code UploadSessionService})은
     * 이 메서드가 아니라 {@link #generateDownloadUrl}을 그대로 써야 한다.
     */
    public String resolveDownloadUrl(String key) {
        try {
            return generateDownloadUrl(key);
        } catch (BusinessException e) {
            log.warn("다운로드 URL 조회 실패 — null로 대체한다. key={}", key, e);
            return null;
        }
    }

    /**
     * key의 확장자로 content type을 추론한다. 지원하지 않는 확장자면 예외를 던진다.
     */
    private String resolveContentType(String key) {
        int dotIndex = key.lastIndexOf('.');  // 문자열에서 마지막 '.' 위치 반환. -1이면 확장자 없음
        if (dotIndex == -1 || dotIndex == key.length() - 1) {
            throw new BusinessException(UploadErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        String extension = key.substring(dotIndex + 1).toLowerCase(Locale.ROOT);  // Locale.ROOT: 터키어 등 특수 로케일의 대소문자 변환 오류 방지 → java-patterns.md
        return Optional.ofNullable(CONTENT_TYPES_BY_EXTENSION.get(extension))  // null 가능 값을 Optional로 감쌈 → java-patterns.md
                .orElseThrow(() -> new BusinessException(UploadErrorCode.UNSUPPORTED_FILE_TYPE));  // 없으면 람다로 예외 생성. () -> ...는 인수 없는 람다(Supplier)
    }

    // todo: presigned url에 저장된 파일이 안전한지 체크해주는 aws 서비스 호출해야 함. 지금은 권한이 없는데 댕글님께 나중에 여쭤보기...
}
