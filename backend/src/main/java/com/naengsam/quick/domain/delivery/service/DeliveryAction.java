package com.naengsam.quick.domain.delivery.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 상태 변경 로직(task)을 엔진의 단일 스레드에서 실행하고, 그 결과 문자열을 future로 돌려주는 액션.
 * 호출자는 future로 블록해 동기 응답을 받는다.
 */
record DeliveryAction(Supplier<String> task, CompletableFuture<String> future) implements Action {

    @Override
    public void execute() {
        try {
            future.complete(task.get());
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
    }
}
