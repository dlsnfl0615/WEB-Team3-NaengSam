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

    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp"
    );

    private final Uploader uploader;

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
     * key의 확장자로 content type을 추론한다. 지원하지 않는 확장자면 예외를 던진다.
     */
    private String resolveContentType(String key) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == key.length() - 1) {
            throw new BusinessException(UploadErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        String extension = key.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return Optional.ofNullable(CONTENT_TYPES_BY_EXTENSION.get(extension))
                .orElseThrow(() -> new BusinessException(UploadErrorCode.UNSUPPORTED_FILE_TYPE));
    }

    // todo: presigned url에 저장된 파일이 안전한지 체크해주는 aws 서비스 호출해야 함. 지금은 권한이 없는데 댕글님께 나중에 여쭤보기...
}
