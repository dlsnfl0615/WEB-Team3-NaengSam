package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Function;

import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.*;

/**
 * 배달 한 건의 상태 전이를 담당한다. 공개 메서드는 동기 요청으로 호출되며, 요청 스레드에서 그대로 실행된다.
 * 상태 검증+변경(check-then-act)은 해당 주문의 DeliveryStatus 객체를 모니터로 삼아 주문 단위로만 직렬화한다.
 * 서로 다른 주문은 서로 다른 모니터라 완전히 병렬로 처리된다.
 *
 * <p>알림(SSE 스텁)은 전이와 같은 락 안에서 실행해 "처리 순서 == 알림 순서"를 보장한다(순서 역전 방지).
 *
 * <p>부르미는 SSE로 상태를 전달받고(현재는 함수 스텁만), 드리미는 5~10초마다 updateDreamiLocation을 호출해 상태를 전달받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryStore store;

    // ===== 공개 메서드 (동기 요청) — 주문 단위 락 안에서 상태 전이를 실행하고 결과를 돌려준다 =====

    // 드리미 위치 정보를 전달 (이 메소드를 5~10초마다 드리미가 호출해야함). 동시에 상태 변경이 있다면 응답한다.
    public DeliveryStatusResponseDto updateDreamiLocation(UUID orderId, GeoPoint dreamiGeoPoint) {
        return transition(orderId, status -> doUpdateDreamiLocation(status, dreamiGeoPoint));
    }

    // 픽업 완료
    public DeliveryStatusResponseDto pickupFinishByDreami(UUID orderId) {
        return transition(orderId, this::doPickupFinishByDreami);
    }

    // "픽업" 과정에서 드리미의 취소
    public DeliveryStatusResponseDto cancelByDreami(UUID orderId) {
        return transition(orderId, this::doCancelByDreami);
    }

    // 픽업 중에 부르미가 취소
    public DeliveryStatusResponseDto cancelByBoormi(UUID orderId) {
        return transition(orderId, this::doCancelByBoormi);
    }

    // 픽업 중에 관리자가 취소
    public DeliveryStatusResponseDto cancelByAdmin(UUID orderId) {
        return transition(orderId, this::doCancelByAdmin);
    }

    // 드리미가 "배달" 완료 (픽업 아님!!)
    public DeliveryStatusResponseDto finishDelivery(UUID orderId) {
        return transition(orderId, this::doFinishDelivery);
    }

    // ===== 주문 단위 직렬화 =====

    // 해당 주문의 DeliveryStatus 객체를 모니터로 삼아 check-then-act를 원자적으로 실행한다.
    // 같은 주문 = 같은 객체 = 직렬화, 다른 주문 = 다른 객체 = 병렬.
    // logic이 상태를 바꾼 뒤(여전히 락 안)의 스냅샷으로 응답 DTO를 만들어 status·message 일관성을 보장한다.
    private DeliveryStatusResponseDto transition(UUID orderId, Function<DeliveryStatus, String> logic) {
        DeliveryStatus deliveryStatus = store.get(orderId);
        synchronized (deliveryStatus) {
            String message = logic.apply(deliveryStatus);
            return DeliveryStatusResponseDto.from(deliveryStatus, message);
        }
    }

    // ===== 실제 상태 전이 로직 (주문 락 안에서 실행) =====

    private String doUpdateDreamiLocation(DeliveryStatus deliveryStatus, GeoPoint dreamiGeoPoint) {
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
        return "위치 갱신됨"; // 상태는 응답 DTO의 status 필드로 전달된다
    }

    private String doPickupFinishByDreami(DeliveryStatus deliveryStatus) {
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

        if (!hasPickupPhoto(deliveryStatus.orderId())) { // 사진이_없는경우
            return "픽업 사진이 없습니다";
        }

        deliveryStatus.setStatus(DELIVERING); // 배달중_정상
        alarmBoormiDeliveringBySSE(); // 부르미에게_배달중_상태로_바뀌었다고_전달_SSE사용()
        return "픽업 완료";
    }

    private String doCancelByDreami(DeliveryStatus deliveryStatus) {
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

    private String doCancelByBoormi(DeliveryStatus deliveryStatus) {
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

    private String doCancelByAdmin(DeliveryStatus deliveryStatus) {
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

    private String doFinishDelivery(DeliveryStatus deliveryStatus) {
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

        if (!hasDeliveryPhoto(deliveryStatus.orderId())) { // 사진이없을때
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
