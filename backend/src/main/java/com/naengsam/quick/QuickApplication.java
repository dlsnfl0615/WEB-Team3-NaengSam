package com.naengsam.quick;

import com.naengsam.quick.domain.user.sms.SolapiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SolapiProperties.class)
public class QuickApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickApplication.class, args);
    }
}
