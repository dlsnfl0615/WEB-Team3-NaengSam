package com.naengsam.quick.domain.delivery.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 배달 상태를 인메모리로 보관한다. 엔진 스레드가 쓰고, SSE 등 다른 스레드가 읽을 수 있어 ConcurrentHashMap을 쓴다.
 */
@Component
public class DeliveryStore {

    private final Map<UUID, DeliveryStatus> statuses = new ConcurrentHashMap<>();

    public DeliveryStatus get(UUID orderId) {
        return statuses.get(orderId);
    }

    public void register(DeliveryStatus deliveryStatus) {
        statuses.put(deliveryStatus.orderId(), deliveryStatus);
    }
}
