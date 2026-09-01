package com.naengsam.quick.domain.upload.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3Client/S3Presigner Bean 등록. {@code upload.s3-enabled=true} 일 때만 등록되므로,
 * AWS 자격증명이 없는 로컬 환경에서는 이 빈들 자체가 만들어지지 않는다.
 * 리전/자격증명은 기본 Provider Chain을 사용한다(로컬은 환경변수/자격증명 파일, 배포 환경은 IAM 역할).
 * 둘 다 {@code SdkAutoCloseable}이므로 destroyMethod="close"로 앱 종료 시 리소스를 정리한다.
 */
@Configuration  // 이 클래스를 Spring 설정 클래스로 선언. 내부 @Bean 메서드들이 빈으로 등록됨
@ConditionalOnProperty(name = "upload.s3-enabled", havingValue = "true")  // 프로퍼티 조건이 맞을 때만 이 클래스 전체를 활성화 → annotations.md
public class S3Config {

    @Bean(destroyMethod = "close")  // 반환 객체를 Spring 컨테이너에 빈으로 등록. 앱 종료 시 close() 자동 호출 → annotations.md
    public S3Client s3Client() {
        return S3Client.create();  // 환경변수·IAM Role 등 Provider Chain으로 자격증명·리전 자동 탐색 → aws-sdk.md
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        return S3Presigner.create();  // URL 서명 전용 클라이언트. 실제 파일 전송은 클라이언트가 직접 함 → aws-sdk.md
    }
}
