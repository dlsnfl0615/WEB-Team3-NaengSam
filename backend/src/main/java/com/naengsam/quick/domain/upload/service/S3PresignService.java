package com.naengsam.quick.domain.upload.service;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 presigned URL 발급 및 실제 업로드 여부 확인을 담당한다. 파일 바이트 자체는 서버를 거치지 않고 클라이언트가 발급받은 URL로 S3에 직접 PUT/GET 한다.
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

    private final UploadProperties uploadProperties;
    private final S3Presigner presigner;
    private final S3Client s3Client;

    /**
     * 업로드 요청자(boormiId)를 key 경로에 포함해 발급한다. key만 알아도 다른 사람이 그 파일을
     * 자기 것인 양 제출하지 못하도록, key 자체에 소유자를 새겨두고 확인 시점에 {@link #validateOwnership}로 대조한다.
     */
    public String buildKey(UUID boormiId, String fileName) {
        return "uploads/" + boormiId + "/" + UUID.randomUUID() + "-" + fileName;
    }

    /**
     * key가 이 boormiId 앞으로 발급된 것인지 확인한다. 아니면(다른 사람 key를 그대로 제출한 경우) 예외를 던진다.
     */
    public void validateOwnership(UUID boormiId, String key) {
        if (!key.startsWith("uploads/" + boormiId + "/")) {
            throw new BusinessException(UploadErrorCode.KEY_OWNER_MISMATCH);
        }
    }

    /**
     * 클라이언트가 이 key로 S3에 직접 PUT 할 수 있는 presigned URL을 발급한다. (10분간 유효)
     */
    public String generateUploadUrl(String key) {

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(uploadProperties.bucketName())
                .key(key)
                .contentType(resolveContentType(key))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) // 만료 10분
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);

        return presignedRequest.url().toString();
    }

    /**
     * 클라이언트가 이 key의 파일을 S3에서 직접 GET 할 수 있는 presigned URL을 발급한다. (5분간 유효)
     * key가 실제로 존재하지 않으면 예외를 던진다.
     */
    public String generateDownloadUrl(String key) {
        if (!isFileUploaded(key)) {
            throw new BusinessException(UploadErrorCode.FILE_NOT_FOUND);
        }

        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(uploadProperties.bucketName())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5)) // 만료 5분
                .getObjectRequest(objectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toString();
    }

    /**
     * 이 key로 실제 S3 객체가 존재하는지(=클라이언트가 presigned URL로 업로드를 완료했는지) 확인한다.
     * 404(아직 업로드 안 됨)는 정상적인 대기 상태라 false로만 반환하고, 그 외 S3 쪽 오류는 예외로 던진다.
     */
    public boolean isFileUploaded(String key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(uploadProperties.bucketName())
                    .key(key)
                    .build();

            s3Client.headObject(headRequest); // 존재하면 정상 응답, 없으면 예외
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false; // 아직 업로드 안 됨
            }
            log.warn("S3 HeadObject 실패: key={}", key, e);
            throw new BusinessException(UploadErrorCode.STORAGE_UPLOAD_FAILED);
        }
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

    // todo: presigned url에 저장된 파일이 안전한지 체크해주는 aws 서비스 호출해야 함
}
