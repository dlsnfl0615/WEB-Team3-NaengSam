package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.delivery.dto.DreamiOfflineDto;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.global.notification.NotificationService;
import com.naengsam.quick.global.notification.SmsFallbackNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 드리미 위치 무소식 감지 단위 테스트. 같은 끊김으로 중복 알림이 가지 않는지, 복구 후 다시 알릴 수 있는지,
 * 그리고 더 긴 임계값에서 유료 SMS 로 승격되며 배달당 1회로 제한되는지를 확인한다.
 */
class DreamiOfflineDetectorTest {

    private static final Duration THRESHOLD = Duration.ofSeconds(30);
    private static final Duration SMS_THRESHOLD = Duration.ofMinutes(3);
    private static final String DREAMI_PHONE = "01012345678";

    private DeliveryRepository deliveryRepository;
    private NotificationService notificationService;
    private SmsFallbackNotifier smsFallbackNotifier;
    private BoormiRepository boormiRepository;
    private DreamiOfflineDetector detector;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        notificationService = mock(NotificationService.class);
        smsFallbackNotifier = mock(SmsFallbackNotifier.class);
        boormiRepository = mock(BoormiRepository.class);
        given(smsFallbackNotifier.isEnabled()).willReturn(true);
        detector = new DreamiOfflineDetector(deliveryRepository, notificationService, smsFallbackNotifier,
                boormiRepository, THRESHOLD, SMS_THRESHOLD);
    }

    @Test
    void 위치_수신이_30초_지나면_부르미에게_오프라인_이벤트를_보낸다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(45));
        givenStaleDeliveries(delivery);

        detector.detectOfflineDreamis();

        ArgumentCaptor<DreamiOfflineDto> payload = ArgumentCaptor.forClass(DreamiOfflineDto.class);
        verify(notificationService).notify(eq(delivery.getBoormiId()),
                eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE), payload.capture());
        assertThat(payload.getValue().orderId()).isEqualTo(delivery.getOrderId());
        assertThat(payload.getValue().secondsSinceLastLocation()).isGreaterThanOrEqualTo(45);
    }

    /** 되돌릴 수 있는 사람은 드리미뿐이라, 부르미 배너와 별개로 드리미 본인도 받아야 한다(이쪽만 웹푸시를 탄다). */
    @Test
    void 위치_수신이_30초_지나면_드리미_본인에게도_오프라인_이벤트를_보낸다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(45));
        givenStaleDeliveries(delivery);

        detector.detectOfflineDreamis();

        ArgumentCaptor<DreamiOfflineDto> payload = ArgumentCaptor.forClass(DreamiOfflineDto.class);
        verify(notificationService).notify(eq(delivery.getDreamiId()),
                eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE_SELF), payload.capture());
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

        // 한 번의 끊김 = 수신자별 1건씩. 드리미 쪽은 웹푸시라 중복이 곧 잠금화면 도배가 된다.
        verify(notificationService, times(1))
                .notify(eq(delivery.getBoormiId()), eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE), any());
        verify(notificationService, times(1))
                .notify(eq(delivery.getDreamiId()), eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE_SELF), any());
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

        verify(notificationService, times(2))
                .notify(eq(delivery.getBoormiId()), eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE), any());
        verify(notificationService, times(2))
                .notify(eq(delivery.getDreamiId()), eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE_SELF), any());
    }

    @Test
    void 무소식_배달이_없으면_아무_알림도_보내지_않는다() {
        givenStaleDeliveries();

        detector.detectOfflineDreamis();

        verify(notificationService, never()).notify(any(), any(), any());
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

    // ===== SMS 승격 =====

    @Test
    void 무소식이_SMS_임계값을_넘으면_드리미_본인에게_문자를_보내고_발송_시각을_남긴다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(200));
        givenStaleDeliveries(delivery);
        givenDreamiPhone(delivery);

        detector.detectOfflineDreamis();

        verify(smsFallbackNotifier).sendDreamiOffline(DREAMI_PHONE);
        // 컬럼이 곧 영속 중복제거다 — 재시작해도 재발송되지 않는다.
        assertThat(delivery.getOfflineSmsSentDtm()).isNotNull();
    }

    @Test
    void 무소식이_SMS_임계값에_못_미치면_문자를_보내지_않는다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(45));
        givenStaleDeliveries(delivery);
        givenDreamiPhone(delivery);

        detector.detectOfflineDreamis();

        verify(smsFallbackNotifier, never()).sendDreamiOffline(anyString());
        assertThat(delivery.getOfflineSmsSentDtm()).isNull();
    }

    /** 인앱·웹푸시(30초)를 이미 보낸 배달도 3분을 넘기면 SMS 로 승격돼야 한다 — 두 판정은 별개다. */
    @Test
    void 이미_인앱_알림을_보낸_배달도_SMS_임계값을_넘기면_문자로_승격된다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(45));
        givenStaleDeliveries(delivery);
        givenDreamiPhone(delivery);
        detector.detectOfflineDreamis();

        // 같은 끊김이 이어져 3분을 넘긴 tick.
        ReflectionTestUtils.setField(delivery, "lastLocationDtm", LocalDateTime.now().minusSeconds(200));
        detector.detectOfflineDreamis();

        verify(notificationService, times(1))
                .notify(eq(delivery.getBoormiId()), eq(DeliveryEventType.DELIVERY_DREAMI_OFFLINE), any());
        verify(smsFallbackNotifier, times(1)).sendDreamiOffline(DREAMI_PHONE);
    }

    @Test
    void 이미_문자를_보낸_배달은_스캔이_반복돼도_다시_보내지_않는다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(200));
        givenStaleDeliveries(delivery);
        givenDreamiPhone(delivery);

        detector.detectOfflineDreamis();
        detector.detectOfflineDreamis();
        detector.detectOfflineDreamis();

        verify(smsFallbackNotifier, times(1)).sendDreamiOffline(DREAMI_PHONE);
    }

    /** 발송 시각이 이미 찍혀 있으면(=재시작 전에 보냈다) 인메모리 상태가 비어 있어도 재발송하지 않는다. */
    @Test
    void 재시작으로_인메모리_기록이_비어도_발송_시각이_있으면_문자를_보내지_않는다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(200));
        ReflectionTestUtils.setField(delivery, "offlineSmsSentDtm", LocalDateTime.now().minusMinutes(1));
        givenStaleDeliveries(delivery);
        givenDreamiPhone(delivery);

        detector.detectOfflineDreamis();

        verify(smsFallbackNotifier, never()).sendDreamiOffline(anyString());
    }

    /** 위치가 다시 들어오면 컬럼이 null 로 되돌아가므로, 같은 배달의 두 번째 진짜 끊김도 알릴 수 있다. */
    @Test
    void 위치가_재개되면_발송_기록이_초기화되어_다음_끊김에_다시_문자를_보낸다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(200));
        givenStaleDeliveries(delivery);
        givenDreamiPhone(delivery);
        detector.detectOfflineDreamis();

        delivery.updateLocation(BigDecimal.ONE, BigDecimal.ONE);
        assertThat(delivery.getOfflineSmsSentDtm()).isNull();

        ReflectionTestUtils.setField(delivery, "lastLocationDtm", LocalDateTime.now().minusSeconds(200));
        detector.detectOfflineDreamis();

        verify(smsFallbackNotifier, times(2)).sendDreamiOffline(DREAMI_PHONE);
    }

    /** 킬 스위치가 꺼진 동안 컬럼을 찍어버리면 나중에 켜도 그 배달은 영구히 알림을 못 받는다. */
    @Test
    void 킬_스위치가_꺼져_있으면_발송_시각을_남기지_않는다() {
        given(smsFallbackNotifier.isEnabled()).willReturn(false);
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(200));
        givenStaleDeliveries(delivery);
        givenDreamiPhone(delivery);

        detector.detectOfflineDreamis();

        verify(smsFallbackNotifier, never()).sendDreamiOffline(anyString());
        assertThat(delivery.getOfflineSmsSentDtm()).isNull();
    }

    @Test
    void 드리미_번호를_찾지_못하면_문자를_보내지_않고_발송_시각도_남기지_않는다() {
        Delivery delivery = trackedDelivery(DeliveryCd.DELIVERING, LocalDateTime.now().minusSeconds(200));
        givenStaleDeliveries(delivery);
        given(boormiRepository.findById(delivery.getDreamiId())).willReturn(Optional.empty());

        detector.detectOfflineDreamis();

        verify(smsFallbackNotifier, never()).sendDreamiOffline(anyString());
        assertThat(delivery.getOfflineSmsSentDtm()).isNull();
    }

    // ===== 픽스처 =====

    private Delivery trackedDelivery(DeliveryCd status, LocalDateTime lastLocationDtm) {
        Delivery delivery = Delivery.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ReflectionTestUtils.setField(delivery, "deliveryCd", status);
        ReflectionTestUtils.setField(delivery, "lastLocationDtm", lastLocationDtm);
        return delivery;
    }

    // 드리미의 계정 행은 같은 UUID 를 PK 로 쓰는 BOORMI 다.
    private void givenDreamiPhone(Delivery delivery) {
        Boormi dreamiAccount = Boormi.create("dreami@test.com", "pw", "드리미", DREAMI_PHONE,
                LocalDate.of(1995, 1, 1));
        given(boormiRepository.findById(delivery.getDreamiId())).willReturn(Optional.of(dreamiAccount));
    }

    @SuppressWarnings("unchecked")
    private void givenStaleDeliveries(Delivery... deliveries) {
        given(deliveryRepository.findStaleLocationDeliveries(
                any(Collection.class), any(LocalDateTime.class)))
                .willReturn(List.of(deliveries));
    }
}
