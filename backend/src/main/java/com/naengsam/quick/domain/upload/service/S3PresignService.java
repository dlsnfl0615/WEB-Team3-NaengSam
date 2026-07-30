package com.naengsam.quick.domain.upload.service;

import java.time.Duration;
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

@Service
@Slf4j
public class S3PresignService {

    private final UploadProperties uploadProperties;
    private final S3Presigner presigner;
    private final S3Client s3Client;

    public S3PresignService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
        this.presigner = S3Presigner.create(); // 리전/자격증명은 기본 Provider Chain 사용
        this.s3Client = S3Client.create(); // 리전/자격증명은 기본 Provider Chain 사용
    }

    // 사진 업로드 용도의 presigned url 생성
    public String generateUploadUrl(String key) {

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(uploadProperties.bucketName())
                .key(key)
                .contentType("image/png") // 필요시 지정
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) // 만료 10분
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);

        return presignedRequest.url().toString();
    }

    // 사진 다운로드 용도의 presigned url 생성
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
}
