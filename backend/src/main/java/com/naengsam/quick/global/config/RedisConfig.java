package com.naengsam.quick.global.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 로그인 대기열이 쓰는 Lua 스크립트를 빈으로 등록한다. 연결·직렬화는 부트가 자동 구성하는
 * {@code StringRedisTemplate}을 그대로 쓴다(대기열 값이 전부 문자열이라 별도 템플릿이 필요 없다).
 *
 * <p>스크립트로 묶는 이유는 원자성뿐이다. "용량을 확인하고 등록", "결과를 쓰고 대기열에서 제거",
 * "결과를 읽고 티켓을 소비"는 두 번의 왕복으로 나누면 동시 요청에서 각각 정원 초과, 순번만 줄고 결과 없음,
 * 세션 중복 생성으로 깨진다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<Long> loginEnqueueScript() {
        return script("scripts/login-enqueue.lua", Long.class);
    }

    @Bean
    public RedisScript<Long> loginAdmitScript() {
        return script("scripts/login-admit.lua", Long.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> loginClaimScript() {
        return script("scripts/login-claim.lua", List.class);
    }

    private <T> RedisScript<T> script(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(resultType);
        return script;
    }
}
