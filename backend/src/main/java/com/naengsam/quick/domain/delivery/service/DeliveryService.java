package com.naengsam.quick.domain.delivery.service;

import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERED;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERING;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_ADMIN;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_BOORMI;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_DREAMI;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_NORMAL;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 배달 한 건의 상태 전이를 담당한다. 공개 메서드는 동기 요청으로 호출되며, 실제 status 검증+변경은 DeliveryAction으로 감싸
 * DeliveryEngine의 단일 스레드에 직렬화한다. 호출자는 CompletableFuture로 블록해 결과 문자열을 돌려받는다.
 *
 * <p>부르미는 SSE로 상태를 전달받고(현재는 함수 스텁만), 드리미는 5~10초마다 updateDreamiLocation을 호출해 상태를 전달받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final long ACTION_TIMEOUT_SECONDS = 3L;

    private final DeliveryEngine engine;
    private final DeliveryStore store;

    // ===== 공개 메서드 (동기 요청) — 상태 변경 로직을 엔진에 제출하고 결과를 기다린다 =====

    // 드리미 위치 정보를 전달 (이 메소드를 5~10초마다 드리미가 호출해야함). 동시에 상태 변경이 있다면 응답한다.
    public String updateDreamiLocation(UUID orderId, GeoPoint dreamiGeoPoint) {
        return submitAndWait(() -> doUpdateDreamiLocation(orderId, dreamiGeoPoint));
    }

    // 픽업 완료
    public String pickupFinishByDreami(UUID orderId) {
        return submitAndWait(() -> doPickupFinishByDreami(orderId));
    }

    // "픽업" 과정에서 드리미의 취소
    public String cancelByDreami(UUID orderId) {
        return submitAndWait(() -> doCancelByDreami(orderId));
    }

    // 픽업 중에 부르미가 취소
    public String cancelByBoormi(UUID orderId) {
        return submitAndWait(() -> doCancelByBoormi(orderId));
    }

    // 픽업 중에 관리자가 취소
    public String cancelByAdmin(UUID orderId) {
        return submitAndWait(() -> doCancelByAdmin(orderId));
    }

    // 드리미가 "배달" 완료 (픽업 아님!!)
    public String finishDelivery(UUID orderId) {
        return submitAndWait(() -> doFinishDelivery(orderId));
    }

    // ===== 엔진 배관 =====

    private String submitAndWait(Supplier<String> task) {
        CompletableFuture<String> future = new CompletableFuture<>();
        engine.submit(new DeliveryAction(task, future));
        try {
            return future.get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e); // TODO: BusinessException으로 정렬
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e); // TODO: BusinessException으로 정렬
        }
    }

    // ===== 실제 상태 전이 로직 (엔진 스레드에서 실행) =====

    private String doUpdateDreamiLocation(UUID orderId, GeoPoint dreamiGeoPoint) {
        DeliveryStatus deliveryStatus = store.get(orderId);

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            alarmDreamiCancelBySSE(); // 드리미에게_SSE로_취소상태_알려주기()
            return "부르미가 취소한 주문입니다";
        }

        // 정상 배달이 아니면 함수 무시 (어차피 조금 지나면 클라이언트에서 이 요청 안보낼거임)
        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            return "이미 배달 완료된 주문입니다";
        }

        deliveryStatus.setLocation(dreamiGeoPoint); // 메모리에_위치정보_수정()
        alarmBoormiLocationBySSE(); // 부르미에게_새로운_위치정보_전달_SSE사용()
        return "현재 상태: " + deliveryStatus.status(); // 드리미에게 현재 상태 정보제공 (TODO: 상태 DTO로 교체)
    }

    private String doPickupFinishByDreami(UUID orderId) {
        DeliveryStatus deliveryStatus = store.get(orderId);

        // 부르미가 취소 눌렀는데 드리미는 아직 인지 못하고 픽업 완료를 누름
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            return "부르미가 이미 취소한 주문입니다";
        }

        // 관리자가 취소 했는데 드리미는 아직 인지 못하고 픽업 완료를 누름
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_관리자의_취소
            return "관리자에 의해 취소한 주문입니다";
        }

        // 정상 "배달중"이면 이미 픽업 완료를 호출한 상황(버튼 연타 등)
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            return "이미 처리 된 요청입니다";
        }

        // 드리미가 픽업 완료를 요청했는데 드리미가 취소하는건 안될듯?
        assert deliveryStatus.status() != PICKUP_CANCELLED_BY_DREAMI; // 픽업중_드리미의_취소

        // 픽업을 완료 하려고 요청했는데 이미 배달이 완료된 건이다?? -> 말이 안됨
        assert deliveryStatus.status() != DELIVERED; // 배달_완료

        // 여기까지 왔으면 이제 "픽업중_정상" 상태만 남음
        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상

        if (!hasPickupPhoto(orderId)) { // 사진이_없는경우
            return "픽업 사진이 없습니다";
        }

        deliveryStatus.setStatus(DELIVERING); // 배달중_정상
        alarmBoormiDeliveringBySSE(); // 부르미에게_배달중_상태로_바뀌었다고_전달_SSE사용()
        return "픽업 완료";
    }

    private String doCancelByDreami(UUID orderId) {
        DeliveryStatus deliveryStatus = store.get(orderId);

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            return "이미 부르미가 먼저 취소한 주문입니다";
        }

        // 이미 배달 중 상태라면, 배달 화면에서 고객센터에 연락하거나 화면 갱신 후 시도
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            return "이미 픽업이 완료된 주문입니다. 다시 시도해주세요";
        }

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_관리자의_취소
            return "이미 관리자에 의해 취소된 주문입니다";
        }

        // 이미 드리미가 취소 요청을 보낸 상황(버튼 연타 등)
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            return "이미 처리된 요청입니다";
        }

        // 배달 완료 상태에서는 픽업 취소 호출이 될 수가 없음
        assert deliveryStatus.status() != DELIVERED; // 배달_완료

        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상
        deliveryStatus.setStatus(PICKUP_CANCELLED_BY_DREAMI); // 픽업중_드리미의_취소
        alarmBoormiDreamiCancelBySSE(); // 부르미에게_픽업중에_드리미가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doCancelByBoormi(UUID orderId) {
        DeliveryStatus deliveryStatus = store.get(orderId);

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            return "이미 드리미가 먼저 취소한 주문";
        }

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_관리자의_취소
            return "이미 관리자가 먼저 취소한 주문";
        }

        // 이미 배달 중 상태라면, 배달 화면에서 고객센터에 연락하거나 화면 갱신 후 시도
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            return "이미 픽업이 완료된 주문입니다. 다시 시도해주세요";
        }

        // 배달 완료 상태에서는 픽업 취소 호출이 될 수가 없음
        assert deliveryStatus.status() != DELIVERED; // 배달_완료

        // 이미 부르미가 취소 요청을 보낸 상황에서 중복 호출될 수 없음
        assert deliveryStatus.status() != PICKUP_CANCELLED_BY_BOORMI; // 픽업중_부르미의_취소

        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상
        deliveryStatus.setStatus(PICKUP_CANCELLED_BY_BOORMI); // 픽업중_부르미의_취소
        // 드리미는 updateDreamiLocation 폴링으로 인지하므로 SSE 알림 없음
        return "픽업 취소 완료";
    }

    private String doCancelByAdmin(UUID orderId) {
        DeliveryStatus deliveryStatus = store.get(orderId);

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            return "이미 드리미가 먼저 취소한 주문";
        }

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            return "이미 부르미가 먼저 취소한 주문";
        }

        // 이미 배달 중 상태라면 취소할 수 없음
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            return "이미 픽업이 완료된 주문입니다. 다시 시도해주세요";
        }

        // 배달 완료 상태에서는 픽업 취소 호출이 될 수가 없음
        assert deliveryStatus.status() != DELIVERED; // 배달_완료

        // 이미 관리자가 취소한 상황에서 중복 호출될 수 없음
        assert deliveryStatus.status() != PICKUP_CANCELLED_BY_ADMIN; // 픽업중_관리자의_취소

        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상
        deliveryStatus.setStatus(PICKUP_CANCELLED_BY_ADMIN); // 픽업중_관리자의_취소

        // 드리미는 5초마다 요청하는 과정에서 상태를 전달받게 됨
        alarmBoormiAdminCancelBySSE(); // 부르미에게_관리자가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doFinishDelivery(UUID orderId) {
        DeliveryStatus deliveryStatus = store.get(orderId);

        // 아마 드리미가 픽업하자마자 바로 배달완료 처리요청하지 않는 이상 없을듯?
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI
                || deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_부르미의_취소 || 픽업중_관리자의_취소
            return "잘못된 접근입니다";
        }

        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            return "이미 배달 완료 처리된 주문입니다";
        }

        // 픽업 중에 배달 완료 요청이 온다?
        if (deliveryStatus.status() == PICKUP_NORMAL) { // 픽업중_정상
            return "잠시 후 다시 시도해주세요";
        }

        // 드리미가 취소 했는데 그 이후에 배달을 완료할 수는 없음
        assert deliveryStatus.status() != PICKUP_CANCELLED_BY_DREAMI; // 픽업중_드리미의_취소

        assert deliveryStatus.status() == DELIVERING; // 배달중_정상

        if (!hasDeliveryPhoto(orderId)) { // 사진이없을때
            return "배달 완료 인증 사진이 없습니다";
        }

        deliveryStatus.setStatus(DELIVERED); // 배달_완료
        alarmBoormiDeliveredBySSE(); // 부르미에게_배달완료라고_전달_SSE로()
        return "드리미에게_완료";
    }

    // ===== SSE 알림 스텁 (TODO: SSE 실제 구현) =====

    private void alarmDreamiCancelBySSE() {
        // TODO: SSE로 드리미에게 취소 상태 전달
        log.debug("[SSE-stub] 드리미에게 취소 상태 알림");
    }

    private void alarmBoormiLocationBySSE() {
        // TODO: SSE로 부르미에게 새 위치 전달
        log.debug("[SSE-stub] 부르미에게 새 위치 알림");
    }

    private void alarmBoormiDeliveringBySSE() {
        // TODO: SSE로 부르미에게 배달중 전환 전달
        log.debug("[SSE-stub] 부르미에게 배달중 전환 알림");
    }

    private void alarmBoormiDreamiCancelBySSE() {
        // TODO: SSE로 부르미에게 드리미의 취소 전달
        log.debug("[SSE-stub] 부르미에게 드리미 취소 알림");
    }

    private void alarmBoormiAdminCancelBySSE() {
        // TODO: SSE로 부르미에게 관리자의 취소 전달
        log.debug("[SSE-stub] 부르미에게 관리자 취소 알림");
    }

    private void alarmBoormiDeliveredBySSE() {
        // TODO: SSE로 부르미에게 배달 완료 전달
        log.debug("[SSE-stub] 부르미에게 배달 완료 알림");
    }

    // ===== 사진 확인 스텁 (TODO: 사진 도메인 연동) =====

    private boolean hasPickupPhoto(UUID orderId) {
        // TODO: 픽업 인증 사진 존재 여부 확인
        return true;
    }

    private boolean hasDeliveryPhoto(UUID orderId) {
        // TODO: 배달 완료 인증 사진 존재 여부 확인
        return true;
    }
}
