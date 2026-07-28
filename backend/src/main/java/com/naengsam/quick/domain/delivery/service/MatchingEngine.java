package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.dto.Action;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 액션을 큐에 넣으면 단일 가상 스레드가 순차적으로 꺼내 실행한다.
 * 모든 매칭 상태 변경을 이 스레드 하나로 직렬화하는 것이 목적이다.
 */
@Component
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final BlockingQueue<Action> queue = new LinkedBlockingQueue<>();

    public void submit(Action action) {
        queue.offer(action);
    }

    @PostConstruct
    public void start() {
        Thread.ofVirtual().name("matching-engine").start(this::run);
    }

    private void run() {
        while (true) {
            try {
                Action action = queue.take();
                action.execute();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // 한 액션의 예외로 소비 스레드 전체가 죽지 않도록 방어한다.
                log.error("매칭 액션 실행 실패", e);
            }
        }
    }
}
