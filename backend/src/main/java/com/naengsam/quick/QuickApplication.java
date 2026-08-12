package com.naengsam.quick;

import com.naengsam.quick.domain.matching.policy.config.MatchingPolicyProperties;
import com.naengsam.quick.domain.upload.service.UploadProperties;
import com.naengsam.quick.domain.user.service.VerificationProperties;
import com.naengsam.quick.domain.user.sms.SolapiProperties;
import com.naengsam.quick.global.notification.WebPushProperties;
import com.naengsam.quick.global.sse.SseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SolapiProperties.class, VerificationProperties.class, UploadProperties.class,
        SseProperties.class,MatchingPolicyProperties.class, WebPushProperties.class})
public class QuickApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickApplication.class, args);
    }
}
