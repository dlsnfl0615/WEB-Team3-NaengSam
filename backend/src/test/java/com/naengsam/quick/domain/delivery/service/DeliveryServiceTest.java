package com.naengsam.quick.domain.delivery.service;

import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERED;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERING;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_BOORMI;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_DREAMI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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

    private BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    private DreamiLocationRequest location(String latitude, String longitude) {
        return new DreamiLocationRequest(new BigDecimal(latitude), new BigDecimal(longitude));
    }

    private List<Function<UUID, DeliveryStatusResponseDto>> cancelOperations() {
        return List.of(
                deliveryService::cancelByDreami,
                deliveryService::cancelByBoormi,
                deliveryService::cancelByAdmin);
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
    void 등록되지않은_배달이면_DELIVERY_NOT_FOUND_예외() {
        UUID orderId = UUID.randomUUID();

        Throwable thrown = catchThrowable(() -> deliveryService.pickupFinishByDreami(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_NOT_FOUND);
    }

    @Test
    void 픽업완료_이미_취소된주문이면_DELIVERY_ALREADY_CANCELLED_예외() {
        for (DeliveryCd cancelledStatus : List.of(
                DeliveryCd.PICKUP_CANCELLED_BY_BOORMI,
                DeliveryCd.PICKUP_CANCELLED_BY_DREAMI,
                DeliveryCd.PICKUP_CANCELLED_BY_ADMIN)) {
            UUID orderId = registerDelivery(cancelledStatus);

            Throwable thrown = catchThrowable(() -> deliveryService.pickupFinishByDreami(orderId));

            assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            assertThat(statusOf(orderId)).isEqualTo(cancelledStatus);
        }
    }

    @Test
    void 픽업완료_이미_처리된단계면_STEP_ALREADY_VERIFIED_예외() {
        UUID orderId = registerDelivery(DELIVERING);

        Throwable thrown = catchThrowable(() -> deliveryService.pickupFinishByDreami(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.STEP_ALREADY_VERIFIED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
    }

    @Test
    void 픽업완료_이미_배달완료된주문이면_DELIVERY_ALREADY_COMPLETED_예외() {
        UUID orderId = registerDelivery(DELIVERED);

        Throwable thrown = catchThrowable(() -> deliveryService.pickupFinishByDreami(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
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
    void 위치갱신_위치정보가_없으면_LOCATION_COLLECTION_FAILED_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        Throwable thrown = catchThrowable(() -> deliveryService.updateDreamiLocation(orderId, null));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.LOCATION_COLLECTION_FAILED);
    }

    @Test
    void 위치갱신_좌표값이_없으면_LOCATION_COLLECTION_FAILED_예외() {
        for (DreamiLocationRequest invalidLocation : List.of(
                new DreamiLocationRequest(null, new BigDecimal("127.0")),
                new DreamiLocationRequest(new BigDecimal("37.5"), null))) {
            UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

            Throwable thrown = catchThrowable(
                    () -> deliveryService.updateDreamiLocation(orderId, invalidLocation));

            assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.LOCATION_COLLECTION_FAILED);
        }
    }

    @Test
    void 위치갱신_좌표를_소수점8자리_HALF_UP으로_저장하고_응답한다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        DeliveryStatusResponseDto result = deliveryService.updateDreamiLocation(
                orderId, location("37.123456789", "127.1"));

        assertThat(result.currentLocation().latitude()).isEqualByComparingTo(new BigDecimal("37.12345679"));
        assertThat(result.currentLocation().latitude().scale()).isEqualTo(8);
        assertThat(result.currentLocation().longitude()).isEqualByComparingTo(new BigDecimal("127.10000000"));
        assertThat(result.currentLocation().longitude().scale()).isEqualTo(8);
        assertThat(store.get(orderId).currentLatitude()).isEqualByComparingTo(new BigDecimal("37.12345679"));
        assertThat(store.get(orderId).currentLongitude()).isEqualByComparingTo(new BigDecimal("127.10000000"));
    }

    @Test
    void 위치갱신_이미_취소된주문이면_DELIVERY_ALREADY_CANCELLED_예외() {
        for (DeliveryCd cancelledStatus : List.of(
                DeliveryCd.PICKUP_CANCELLED_BY_BOORMI,
                DeliveryCd.PICKUP_CANCELLED_BY_DREAMI,
                DeliveryCd.PICKUP_CANCELLED_BY_ADMIN)) {
            UUID orderId = registerDelivery(cancelledStatus);

            Throwable thrown = catchThrowable(
                    () -> deliveryService.updateDreamiLocation(orderId, location("37.5", "127.0")));

            assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }
    }

    @Test
    void 위치갱신_이미_배달완료된주문이면_DELIVERY_ALREADY_COMPLETED_예외() {
        UUID orderId = registerDelivery(DELIVERED);

        Throwable thrown = catchThrowable(
                () -> deliveryService.updateDreamiLocation(orderId, location("37.5", "127.0")));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
    }

    @Test
    void 취소_이미_취소된주문이면_DELIVERY_ALREADY_CANCELLED_예외() {
        for (Function<UUID, DeliveryStatusResponseDto> cancelOperation : cancelOperations()) {
            for (DeliveryCd cancelledStatus : List.of(
                    DeliveryCd.PICKUP_CANCELLED_BY_BOORMI,
                    DeliveryCd.PICKUP_CANCELLED_BY_DREAMI,
                    DeliveryCd.PICKUP_CANCELLED_BY_ADMIN)) {
                UUID orderId = registerDelivery(cancelledStatus);

                Throwable thrown = catchThrowable(() -> cancelOperation.apply(orderId));

                assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
                assertThat(statusOf(orderId)).isEqualTo(cancelledStatus);
            }
        }
    }

    @Test
    void 취소_배달중인주문이면_CANCELLATION_RESTRICTED_DURING_DELIVERY_예외() {
        for (Function<UUID, DeliveryStatusResponseDto> cancelOperation : cancelOperations()) {
            UUID orderId = registerDelivery(DELIVERING);

            Throwable thrown = catchThrowable(() -> cancelOperation.apply(orderId));

            assertThat(errorCodeOf(thrown))
                    .isEqualTo(DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY);
            assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
        }
    }

    @Test
    void 취소_배달완료된주문이면_DELIVERY_ALREADY_COMPLETED_예외() {
        for (Function<UUID, DeliveryStatusResponseDto> cancelOperation : cancelOperations()) {
            UUID orderId = registerDelivery(DELIVERED);

            Throwable thrown = catchThrowable(() -> cancelOperation.apply(orderId));

            assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
            assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
        }
    }

    @Test
    void 배달완료_픽업전이면_PICKUP_NOT_COMPLETED_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        Throwable thrown = catchThrowable(() -> deliveryService.finishDelivery(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.PICKUP_NOT_COMPLETED);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
    }

    @Test
    void 배달완료_이미_취소된주문이면_DELIVERY_ALREADY_CANCELLED_예외() {
        for (DeliveryCd cancelledStatus : List.of(
                DeliveryCd.PICKUP_CANCELLED_BY_BOORMI,
                DeliveryCd.PICKUP_CANCELLED_BY_DREAMI,
                DeliveryCd.PICKUP_CANCELLED_BY_ADMIN)) {
            UUID orderId = registerDelivery(cancelledStatus);

            Throwable thrown = catchThrowable(() -> deliveryService.finishDelivery(orderId));

            assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            assertThat(statusOf(orderId)).isEqualTo(cancelledStatus);
        }
    }

    @Test
    void 배달완료_이미_완료된주문이면_DELIVERY_ALREADY_COMPLETED_예외() {
        UUID orderId = registerDelivery(DELIVERED);

        Throwable thrown = catchThrowable(() -> deliveryService.finishDelivery(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
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
            AtomicReference<Throwable> pickupError = new AtomicReference<>();
            AtomicReference<Throwable> cancelError = new AtomicReference<>();
            runConcurrently(
                    () -> pickupError.set(
                            catchThrowable(() -> pickupResult.set(deliveryService.pickupFinishByDreami(orderId)))),
                    () -> cancelError.set(
                            catchThrowable(() -> cancelResult.set(deliveryService.cancelByBoormi(orderId)))));

            boolean pickupWon = pickupResult.get() != null
                    && errorCodeOf(cancelError.get())
                    == DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY
                    && statusOf(orderId) == DELIVERING;
            boolean cancelWon = cancelResult.get() != null
                    && errorCodeOf(pickupError.get()) == DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED
                    && statusOf(orderId) == PICKUP_CANCELLED_BY_BOORMI;

            // 픽업/취소 중 정확히 한쪽만 성공(둘 다 성공하는 레이스가 없어야 함)
            assertThat(pickupWon ^ cancelWon)
                    .as("iteration %d: pickup=%s, pickupError=%s, cancel=%s, cancelError=%s, status=%s",
                            i, pickupResult.get(), pickupError.get(), cancelResult.get(), cancelError.get(),
                            statusOf(orderId))
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
