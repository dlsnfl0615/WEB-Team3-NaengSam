package com.naengsam.quick.domain.upload.service;

import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
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
 * 실제 S3에 대고 presigned URL을 발급/확인한다. {@code upload.s3-enabled=true} 일 때만 빈으로 등록된다.
 */
@Component  // @Service/@Repository와 달리 역할 구분 없는 범용 빈 등록. 여기선 인프라 구현체라 @Component 사용
@RequiredArgsConstructor  // final 필드를 인수로 받는 생성자를 Lombok이 자동 생성. Spring이 이 생성자로 의존성 주입
@Slf4j  // Lombok이 private static final Logger log = ... 필드를 자동 생성
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "true")  // s3-enabled=true일 때만 이 빈 등록(DevUploader와 상호 배타) → annotations.md
public class S3Uploader implements Uploader {

    private final UploadProperties uploadProperties;
    private final S3Presigner presigner;
    private final S3Client s3Client;

    @Override
    public String generateUploadUrl(String key, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()  // AWS SDK는 모두 빌더 패턴. 불변 객체를 체이닝으로 생성 → aws-sdk.md
                .bucket(uploadProperties.bucketName())  // record의 접근자는 get 없이 필드명()으로 호출
                .key(key)
                .contentType(contentType)
                .build();  // 빌더 최종 단계. 이 시점에 불변 객체 생성

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))  // java.time.Duration: 시간 간격 표현. ofMinutes/ofSeconds/ofHours 등 팩토리 제공 → java-patterns.md
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);  // S3Presigner: URL에 서명만 찍음. 실제 업로드는 클라이언트가 이 URL로 PUT → aws-sdk.md

        return presignedRequest.url().toString();
    }

    @Override
    public String generateDownloadUrl(String key) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()  // GET 요청용 빌더. PutObjectRequest와 구조 동일 → aws-sdk.md
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

    @Override
    public boolean exists(String key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()  // HEAD 요청: 파일 메타데이터만 조회(Body 없음). 존재 여부 확인에 사용 → aws-sdk.md
                    .bucket(uploadProperties.bucketName())
                    .key(key)
                    .build();

            s3Client.headObject(headRequest); // 존재하면 정상 응답, 없으면 예외
            return true;
        } catch (S3Exception e) {  // AWS SDK 전용 예외. statusCode()로 HTTP 상태코드 조회 가능 → aws-sdk.md
            if (e.statusCode() == 404) {  // S3에 객체가 없을 때 돌아오는 HTTP 404
                return false; // 아직 업로드 안 됨
            }
            log.warn("S3 HeadObject 실패: key={}", key, e);  // {}는 SLF4J 파라미터 치환자. String.format의 %s와 유사, 예외는 마지막 인수로 스택트레이스 출력
            throw new BusinessException(UploadErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }
}
