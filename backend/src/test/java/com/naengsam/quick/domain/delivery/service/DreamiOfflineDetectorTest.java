package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.dto.DreamiOfflineDto;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.global.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 드리미 위치 무소식 감지 단위 테스트. 같은 끊김으로 중복 알림이 가지 않는지와, 복구 후 다시 알릴 수 있는지를 확인한다.
 */
class DreamiOfflineDetectorTest {

    private static final Duration THRESHOLD = Duration.ofSeconds(30);

    private DeliveryRepository deliveryRepository;
    private SseService sseService;
    private DreamiOfflineDetector detector;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        sseService = mock(SseService.class);
        detector = new DreamiOfflineDetector(deliveryRepository, sseService, THRESHOLD);
    }

    @Test
    void 위치_수신이_30초_지나면_부르미에게_오프라인_이벤트를_보낸다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(45));
        givenStaleDeliveries(delivery);

        detector.detectOfflineDreamis();

        ArgumentCaptor<DreamiOfflineDto> payload = ArgumentCaptor.forClass(DreamiOfflineDto.class);
        verify(sseService).send(eq(delivery.getBoormiId()),
                eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE), payload.capture());
        assertThat(payload.getValue().orderId()).isEqualTo(delivery.getOrderId());
        assertThat(payload.getValue().secondsSinceLastLocation()).isGreaterThanOrEqualTo(45);
    }

    @Test
    void 이미_알린_배달은_다시_알리지_않는다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(45));
        givenStaleDeliveries(delivery);

        detector.detectOfflineDreamis();
        detector.detectOfflineDreamis();
        detector.detectOfflineDreamis();

        verify(sseService, times(1)).send(any(), any(), any());
    }

    @Test
    void 위치가_다시_들어오면_알림_기록에서_빠져_재알림이_가능하다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(45));
        givenStaleDeliveries(delivery);
        detector.detectOfflineDreamis();

        // 위치가 다시 들어와 무소식 목록에서 빠진 tick — 이때 알림 기록도 함께 정리돼야 한다.
        givenStaleDeliveries();
        detector.detectOfflineDreamis();

        // 다시 끊기면 새 끊김이므로 또 알려야 한다.
        givenStaleDeliveries(delivery);
        detector.detectOfflineDreamis();

        verify(sseService, times(2)).send(any(), any(), any());
    }

    @Test
    void 무소식_배달이_없으면_아무_알림도_보내지_않는다() {
        givenStaleDeliveries();

        detector.detectOfflineDreamis();

        verify(sseService, never()).send(any(), any(), any());
    }

    @Test
    void 위치_이력이_없는_배달은_오프라인으로_보지_않는다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, null);

        assertThat(detector.isOffline(delivery)).isFalse();
        assertThat(detector.secondsSinceLastLocationOrNull(delivery)).isNull();
    }

    @Test
    void 추적_대상_상태가_아니면_오프라인으로_보지_않는다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERED, LocalDateTime.now().minusSeconds(600));

        assertThat(detector.isOffline(delivery)).isFalse();
    }

    @Test
    void 임계값_안에_위치를_받았으면_오프라인이_아니다() {
        Delivery delivery = trackedDelivery(DeliveryCd.PICKUP_NORMAL, LocalDateTime.now().minusSeconds(7));

        assertThat(detector.isOffline(delivery)).isFalse();
        assertThat(detector.secondsSinceLastLocationOrNull(delivery)).isGreaterThanOrEqualTo(7);
    }

    // ===== 픽스처 =====

    private Delivery trackedDelivery(DeliveryCd status, LocalDateTime lastLocationDtm) {
        Delivery delivery = Delivery.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "deliveryCd", status);
        ReflectionTestUtils.setField(delivery, "lastLocationDtm", lastLocationDtm);
        return delivery;
    }

    @SuppressWarnings("unchecked")
    private void givenStaleDeliveries(Delivery... deliveries) {
        given(deliveryRepository.findStaleLocationDeliveries(
                any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(deliveries));
    }
}
