package com.naengsam.quick.domain.delivery.service;

import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERED;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERING;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_BOORMI;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_DREAMI;
import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 배달 상태 전이 서비스 단위 테스트. 전이 가드 분기와, 주문 단위 락으로 보장되는 동시성(같은 주문 직렬화 / 다른 주문 병렬)을 확인한다.
 */
class DeliveryServiceTest {

    private DeliveryStore store;
    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        store = new DeliveryStore();
        deliveryService = new DeliveryService(store);
    }

    // ===== 픽스처 =====

    // 주어진 상태로 배달 한 건을 store에 등록하고 orderId를 돌려준다.
    private UUID registerDelivery(DeliveryCd status) {
        UUID orderId = UUID.randomUUID();
        DeliveryStatus deliveryStatus = DeliveryStatus.create(orderId, UUID.randomUUID(), UUID.randomUUID());
        ReflectionTestUtils.setField(deliveryStatus, "status", status);
        store.register(deliveryStatus);
        return orderId;
    }

    private DeliveryCd statusOf(UUID orderId) {
        return store.get(orderId).status();
    }

    // ===== 배달 시작 =====

    @Test
    void 배달시작하면_PICKUP_NORMAL로_store에_등록된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();

        deliveryService.startDelivery(orderId, dreamiId, boormiId);

        DeliveryStatus registered = store.get(orderId);
        assertThat(registered).isNotNull();
        assertThat(registered.status()).isEqualTo(DeliveryCd.PICKUP_NORMAL);
        assertThat(registered.dreamiId()).isEqualTo(dreamiId);
        assertThat(registered.boormiId()).isEqualTo(boormiId);
    }

    // ===== 상태 전이 단위 테스트 =====

    @Test
    void 픽업완료_정상이면_DELIVERING으로_전이() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        DeliveryStatusResponseDto result = deliveryService.pickupFinishByDreami(orderId);

        assertThat(result.message()).isEqualTo("픽업 완료");
        assertThat(result.status()).isEqualTo(DELIVERING);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
    }

    @Test
    void 픽업완료_이미_부르미가_취소한주문이면_전이하지않음() {
        UUID orderId = registerDelivery(PICKUP_CANCELLED_BY_BOORMI);

        DeliveryStatusResponseDto result = deliveryService.pickupFinishByDreami(orderId);

        assertThat(result.message()).isEqualTo("부르미가 이미 취소한 주문입니다");
        assertThat(result.status()).isEqualTo(PICKUP_CANCELLED_BY_BOORMI);
        assertThat(statusOf(orderId)).isEqualTo(PICKUP_CANCELLED_BY_BOORMI);
    }

    @Test
    void 드리미취소_정상이면_PICKUP_CANCELLED_BY_DREAMI로_전이() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        DeliveryStatusResponseDto result = deliveryService.cancelByDreami(orderId);

        assertThat(result.message()).isEqualTo("픽업 취소 완료");
        assertThat(result.status()).isEqualTo(PICKUP_CANCELLED_BY_DREAMI);
        assertThat(statusOf(orderId)).isEqualTo(PICKUP_CANCELLED_BY_DREAMI);
    }

    @Test
    void 배달완료_배달중이면_DELIVERED로_전이() {
        UUID orderId = registerDelivery(DELIVERING);

        DeliveryStatusResponseDto result = deliveryService.finishDelivery(orderId);

        assertThat(result.message()).isEqualTo("드리미에게_완료");
        assertThat(result.status()).isEqualTo(DELIVERED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
    }

    @Test
    void 위치갱신_이미_배달완료된주문이면_무시() {
        UUID orderId = registerDelivery(DELIVERED);

        DeliveryStatusResponseDto result = deliveryService.updateDreamiLocation(orderId, new GeoPoint(37.5, 127.0));

        assertThat(result.message()).isEqualTo("이미 배달 완료된 주문입니다");
        assertThat(result.status()).isEqualTo(DELIVERED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
    }

    // ===== 동시성 =====

    // 같은 주문에 픽업완료와 부르미취소가 동시에 들어와도, 주문 락으로 직렬화되어 정확히 한쪽만 전이에 성공해야 한다.
    // 락이 없다면 둘 다 PICKUP_NORMAL을 읽고 둘 다 전이(레이스)될 수 있다. 반복 실행으로 불변식을 검증한다.
    @Test
    void 같은주문에_픽업완료와_부르미취소가_동시요청되면_정확히_한쪽만_성공() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

            AtomicReference<DeliveryStatusResponseDto> pickupResult = new AtomicReference<>();
            AtomicReference<DeliveryStatusResponseDto> cancelResult = new AtomicReference<>();
            runConcurrently(
                    () -> pickupResult.set(deliveryService.pickupFinishByDreami(orderId)),
                    () -> cancelResult.set(deliveryService.cancelByBoormi(orderId)));

            boolean pickupWon = "픽업 완료".equals(pickupResult.get().message())
                    && "이미 픽업이 완료된 주문입니다. 다시 시도해주세요".equals(cancelResult.get().message())
                    && statusOf(orderId) == DELIVERING;
            boolean cancelWon = "픽업 취소 완료".equals(cancelResult.get().message())
                    && "부르미가 이미 취소한 주문입니다".equals(pickupResult.get().message())
                    && statusOf(orderId) == PICKUP_CANCELLED_BY_BOORMI;

            // 픽업/취소 중 정확히 한쪽만 성공(둘 다 성공하는 레이스가 없어야 함)
            assertThat(pickupWon ^ cancelWon)
                    .as("iteration %d: pickup=%s, cancel=%s, status=%s",
                            i, pickupResult.get().message(), cancelResult.get().message(), statusOf(orderId))
                    .isTrue();
        }
    }

    @Test
    void 서로다른주문은_병렬로_각자_전이된다() throws InterruptedException {
        UUID orderA = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID orderB = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        runConcurrently(
                () -> deliveryService.pickupFinishByDreami(orderA),
                () -> deliveryService.cancelByBoormi(orderB));

        assertThat(statusOf(orderA)).isEqualTo(DELIVERING);
        assertThat(statusOf(orderB)).isEqualTo(PICKUP_CANCELLED_BY_BOORMI);
    }

    // 두 작업을 CountDownLatch로 최대한 같은 순간에 시작시켜 실행한다.
    private void runConcurrently(Runnable first, Runnable second) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Runnable[] tasks = {first, second};
        for (Runnable task : tasks) {
            new Thread(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
