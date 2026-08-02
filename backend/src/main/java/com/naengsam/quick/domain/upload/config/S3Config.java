package com.naengsam.quick.domain.upload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3Client/S3Presigner Bean 등록. 리전/자격증명은 기본 Provider Chain을 사용한다
 * (로컬은 환경변수/자격증명 파일, 배포 환경은 IAM 역할 - 별도 설정 불필요).
 * 둘 다 {@code SdkAutoCloseable}이므로 destroyMethod="close"로 앱 종료 시 리소스를 정리한다.
 */
@Configuration
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        return S3Client.create();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        return S3Presigner.create();
    }
}
