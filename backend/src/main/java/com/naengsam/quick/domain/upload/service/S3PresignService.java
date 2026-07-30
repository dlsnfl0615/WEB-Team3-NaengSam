package com.naengsam.quick.domain.upload.service;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    public S3PresignService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
        this.presigner = S3Presigner.create(); // 리전/자격증명은 기본 Provider Chain 사용
        this.s3Client = S3Client.create(); // 리전/자격증명은 기본 Provider Chain 사용
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
     */
    public String generateDownloadUrl(String key) {

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
            log.debug(e.getClass().getName().toString());
            return false; // 아직 업로드 안 됨
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
