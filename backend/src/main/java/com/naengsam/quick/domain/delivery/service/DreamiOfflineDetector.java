package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.dto.DreamiOfflineDto;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.global.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 드리미 위치 전송이 끊겼는지 판정해 부르미와 드리미 본인에게 알린다.
 *
 * <p><b>왜 이벤트를 둘로 나눴는가.</b> 판정도 payload도 같지만 채널이 다르다. 부르미는 추적 화면을 보고 있어
 * 인앱 배너면 되고, 드리미는 앱이 죽었거나 백그라운드라서 무소식인 것이므로 웹푸시로 깨워야 한다. 알림 채널
 * 결정표가 이벤트 이름을 키로 쓰기 때문에, 한 이름으로는 수신자별로 다른 채널을 걸 수 없다.
 *
 * <p><b>왜 서버가 판정하는가.</b> 끊김은 세 구간(드리미→서버 / 서버→부르미 / 부르미→인터넷) 중 어디서든
 * 생길 수 있는데, 부르미 클라이언트는 첫 구간을 관측할 수 없다. 클라이언트가 "위치가 안 온다"만 보고 판정하면
 * 자기 네트워크 장애를 드리미 장애로 오해하고, 화면 재진입 시 "아직 못 받음"과 "끊김"을 구분할 수 없다.
 * 첫 구간의 유일한 목격자인 서버가 판정하고, 부르미 클라이언트는 자기 SSE 연결 상태만 따로 판단한다.
 *
 * <p><b>복구 알림은 없다.</b> 드리미가 돌아오면 위치 전송이 재개되며 기존 delivery_location 이벤트가
 * 다시 흐르므로, 클라이언트는 그걸 받아 배너를 내린다.
 *
 * <p>상태는 전부 인메모리다(SseEmitterRegistry·매칭 레지스트리와 동일한 단일 JVM 전제).
 */
@Slf4j
@Service
public class DreamiOfflineDetector {

    // 부르미가 '움직이는 드리미'를 보고 있는 상태들. 그 외(완료·취소·반송)는 추적 대상이 아니다.
    private static final Set<DeliveryCd> TRACKED_STATUSES =
            EnumSet.of(DeliveryCd.PICKUP_NORMAL, DeliveryCd.PICKUP_DELAYED, DeliveryCd.DELIVERING);

    private final DeliveryRepository deliveryRepository;
    private final NotificationService notificationService;
    private final Duration offlineThreshold;

    // 이미 알린 주문. 같은 끊김으로 매 tick마다 중복 알림이 가지 않게 막는다.
    private final Set<UUID> notifiedOrders = ConcurrentHashMap.newKeySet();

    public DreamiOfflineDetector(DeliveryRepository deliveryRepository, NotificationService notificationService,
            @Value("${delivery.dreami-offline-threshold}") Duration offlineThreshold) {
        this.deliveryRepository = deliveryRepository;
        this.notificationService = notificationService;
        this.offlineThreshold = offlineThreshold;
    }

    /**
     * 무소식 배달을 찾아 아직 알리지 않은 건만 부르미에게 통보한다.
     *
     * <p>스캔 간격이 곧 감지 지연의 상한이다(실제 지연 = 임계값 + 최대 스캔 간격).
     */
    @Scheduled(fixedDelayString = "${delivery.dreami-offline-scan-interval}")
    @Transactional(readOnly = true)
    public void detectOfflineDreamis() {
        // 몇 초 간격으로 도는 작업이라 예외를 그대로 던지면 일시적인 DB 장애에 스택트레이스가 로그를 덮는다.
        // 한 번 실패해도 다음 tick이 다시 시도하므로 여기서 요약만 남긴다(SSE heartbeat 스케줄러와 같은 방식).
        try {
            notifyStaleDeliveries();
        } catch (RuntimeException e) {
            log.warn("드리미 위치 끊김 감지 실패 — 다음 주기에 다시 시도한다: {}", e.getMessage());
        }
    }

    private void notifyStaleDeliveries() {
        LocalDateTime threshold = LocalDateTime.now().minus(offlineThreshold);
        List<Delivery> staleDeliveries =
                deliveryRepository.findStaleLocationDeliveries(TRACKED_STATUSES, threshold);

        // 지금 무소식인 주문만 남긴다 — 위치가 다시 들어온 건과 종료된 건이 여기서 자동으로 빠지므로,
        // 나중에 다시 끊기면 다시 알릴 수 있다(별도 플래그 컬럼이나 정리 로직이 필요 없다).
        Set<UUID> staleOrderIds = staleDeliveries.stream()
                .map(Delivery::getOrderId)
                .collect(Collectors.toSet());
        notifiedOrders.retainAll(staleOrderIds);

        for (Delivery delivery : staleDeliveries) {
            if (!notifiedOrders.add(delivery.getOrderId())) {
                continue; // 이미 알린 건
            }
            long elapsedSeconds = secondsSinceLastLocation(delivery);
            log.info("드리미 위치 끊김 감지 — orderId={}, {}초 무소식", delivery.getOrderId(), elapsedSeconds);
            DreamiOfflineDto payload = new DreamiOfflineDto(delivery.getOrderId(), elapsedSeconds);

            notificationService.notify(delivery.getBoormiId(), DeliveryEventType.DELIVERY_DREAMI_OFFLINE, payload);
            // 드리미 본인에게도 알린다. 되돌릴 수 있는 유일한 사람이라서다 — 배너를 본 부르미는 할 수 있는 게 없다.
            // 이벤트 이름을 나눈 덕에 이쪽만 웹푸시를 타므로, 앱이 백그라운드로 밀려 무소식이 된 흔한 경우에도 닿는다.
            notificationService.notify(delivery.getDreamiId(), DeliveryEventType.DELIVERY_DREAMI_OFFLINE_SELF, payload);
        }
    }

    /**
     * 마지막 위치 수신 이후 흐른 시간(초). 위치를 한 번도 받지 못했으면 null.
     *
     * <p>상세 조회가 이 값을 응답에 담아, 화면을 다시 열었을 때 이벤트를 기다리지 않고 즉시 상태를 복원한다.
     */
    public Long secondsSinceLastLocationOrNull(Delivery delivery) {
        if (delivery.getLastLocationDtm() == null) {
            return null;
        }
        return secondsSinceLastLocation(delivery);
    }

    /** 지금 이 배달의 드리미가 끊긴 상태인지. 추적 대상 상태가 아니거나 위치 이력이 없으면 false. */
    public boolean isOffline(Delivery delivery) {
        if (delivery.getLastLocationDtm() == null || !TRACKED_STATUSES.contains(delivery.getDeliveryCd())) {
            return false;
        }
        return secondsSinceLastLocation(delivery) >= offlineThreshold.toSeconds();
    }

    private long secondsSinceLastLocation(Delivery delivery) {
        return Duration.between(delivery.getLastLocationDtm(), LocalDateTime.now()).toSeconds();
    }
}
