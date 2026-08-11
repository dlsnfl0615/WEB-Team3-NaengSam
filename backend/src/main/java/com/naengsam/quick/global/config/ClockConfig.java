package com.naengsam.quick.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스 코드에서 LocalDateTime.now()를 직접 호출하지 않고 주입받은 Clock을 쓰도록 하기 위한 빈.
 * 테스트에서는 Clock.fixed(...)로 교체해 시각을 고정할 수 있다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
