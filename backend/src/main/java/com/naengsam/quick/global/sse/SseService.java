package com.naengsam.quick.global.sse;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 도메인이 실시간 이벤트를 push할 때 쓰는 공개 파사드. 연결 수립은 {@link #subscribe}, 이벤트 전송은 {@link #send}로 한다.
 *
 * <p>실제 전송은 단일 가상 스레드로 오프로딩한다. 이렇게 하면 호출 스레드(예: 매칭 엔진의 단일 스레드)가 느리거나 죽은 클라이언트 때문에 막히지 않으며, 전송 스레드가 단일이라
 * 이벤트 순서도 발생 순서대로 보존된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseService {

    private final SseEmitterRegistry registry;

    private final ExecutorService sender =
            Executors.newSingleThreadExecutor(r -> Thread.ofVirtual().name("sse-sender").unstarted(r));

    public SseEmitter subscribe(UUID userId) {
        return registry.connect(userId);
    }

    public void send(UUID userId, SseEventType type, Object payload) {
        sender.submit(() -> registry.send(userId, type.eventName(), payload));
    }

    @PreDestroy
    void shutdown() {
        sender.shutdown();
    }
}
