package com.naengsam.quick.domain.delivery.service;

import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERED;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.DELIVERING;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_ADMIN;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_BOORMI;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_CANCELLED_BY_DREAMI;
import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.PICKUP_NORMAL;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.domain.upload.service.UploadSessionService;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.sse.SseService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 배달 한 건의 상태 전이를 담당한다. 공개 메서드는 동기 요청으로 호출되며, 요청 스레드에서 그대로 실행된다.
 * 상태 검증+변경(check-then-act)은 해당 주문의 DeliveryStatus 객체를 모니터로 삼아 주문 단위로만 직렬화한다.
 * 서로 다른 주문은 서로 다른 모니터라 완전히 병렬로 처리된다.
 *
 * <p>알림(SSE)은 전이와 같은 락 안에서 불변 스냅샷(DeliveryStatusResponseDto)을 만들어 SseService로 넘긴다.
 * 실제 전송은 SseService가 단일 가상 스레드로 async 오프로딩하므로 "처리 순서 == 알림 순서"가 보장되고 호출 스레드는 막히지 않는다.
 *
 * <p>부르미·드리미 모두 상태 변경은 SSE로 전달받는다. updateDreamiLocation은 드리미가 5~10초마다 호출해
 * 위치만 전송하는 용도이며(응답은 ack), 취소/완료된 주문에 대한 가드 예외는 폴링 중단 신호로만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final int LOCATION_SCALE = 8;

    private final DeliveryStore store;
    private final SseService sseService;
    private final S3PresignService s3PresignService;
    private final UploadSessionService uploadSessionService;

    // ===== 배달 시작 (진입점) =====

    // 매칭이 확정된 주문의 배달을 시작한다. 초기 상태 PICKUP_NORMAL로 DeliveryStatus를 만들어 store에 등록해,
    // 이후 상태 전이 요청들이 store.get(orderId)로 이 주문을 찾을 수 있게 한다.
    // 호출부(매칭 확정 훅)는 매칭 도메인 담당이며 여기서는 진입점만 제공한다. boormiId는 호출부에서 넘겨받는다.
    public void startDelivery(UUID orderId, UUID dreamiId, UUID boormiId) {
        store.register(DeliveryStatus.create(orderId, dreamiId, boormiId));
    }

    // ===== 공개 메서드 (동기 요청) — 주문 단위 락 안에서 상태 전이를 실행하고 결과를 돌려준다 =====

    // 드리미 위치 정보를 전달 (이 메소드를 5~10초마다 드리미가 호출해야함). 위치만 갱신하고 응답은 ack(void)다.
    public void updateDreamiLocation(UUID orderId, DreamiLocationRequest location) {
        transition(orderId, status -> {
            doUpdateDreamiLocation(status, location);
        });
    }

    // 픽업 완료 (드리미가 업로드한 픽업 인증 사진 key를 함께 받아 검증한다)
    public DeliveryStatusResponseDto pickupFinishByDreami(UUID orderId, UUID dreamiId, String photoKey) {
        return transition(orderId, deliveryStatus -> {
            return doPickupFinishByDreami(deliveryStatus, dreamiId, photoKey);
        });
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

    // 드리미가 "배달" 완료 (픽업 아님!!) — 배달 완료 인증 사진 key를 함께 받아 검증한다
    public DeliveryStatusResponseDto finishDelivery(UUID orderId, UUID dreamiId, String photoKey) {
        return transition(orderId, deliveryStatus -> {
            return doFinishDelivery(deliveryStatus, dreamiId, photoKey);
        });
    }

    // ===== 주문 단위 직렬화 =====

    // 해당 주문의 DeliveryStatus 객체를 모니터로 삼아 check-then-act를 원자적으로 실행한다.
    // 같은 주문 = 같은 객체 = 직렬화, 다른 주문 = 다른 객체 = 병렬.
    // logic이 상태를 바꾼 뒤(여전히 락 안)의 스냅샷으로 응답 DTO를 만들어 status·message 일관성을 보장한다.
    private DeliveryStatusResponseDto transition(UUID orderId, Function<DeliveryStatus, String> logic) {
        DeliveryStatus deliveryStatus = store.get(orderId);
        if (deliveryStatus == null) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND);
        }

        synchronized (deliveryStatus) {
            String message = logic.apply(deliveryStatus);
            return DeliveryStatusResponseDto.from(deliveryStatus, message);
        }
    }

    // 상태 스냅샷 DTO가 필요 없는 전이(위치 갱신 등)용. 주문 단위 락은 위와 동일하게 잡고, 응답 DTO는 만들지 않는다
    // (컨트롤러가 void를 반환하면 CommonResponse가 result=null 성공 봉투로 감싼다).
    private void transition(UUID orderId, Consumer<DeliveryStatus> logic) {
        DeliveryStatus deliveryStatus = store.get(orderId);
        if (deliveryStatus == null) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND);
        }

        synchronized (deliveryStatus) {
            logic.accept(deliveryStatus);
        }
    }

    // ===== 실제 상태 전이 로직 (주문 락 안에서 실행) =====

    // 위치만 갱신하고 부르미에게 SSE로 전달한다. 취소/완료 상태에 대한 예외는 드리미 폴링을 멈추게 하는 신호로 남긴다
    // (취소 알림 자체는 취소 시점에 이미 드리미에게 SSE로 push된다).
    private void doUpdateDreamiLocation(DeliveryStatus deliveryStatus, DreamiLocationRequest location) {
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI // 픽업중_부르미의_취소
                || deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI
                || deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 이미 완료된 주문이면 위치를 더 이상 갱신하지 않음
        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        }

        if (location == null || location.latitude() == null || location.longitude() == null) {
            throw new BusinessException(DeliveryErrorCode.LOCATION_COLLECTION_FAILED);
        }

        BigDecimal latitude = location.latitude().setScale(LOCATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal longitude = location.longitude().setScale(LOCATION_SCALE, RoundingMode.HALF_UP);
        deliveryStatus.setLocation(latitude, longitude); // 메모리에_위치정보_수정()
        alarmBoormiLocationBySSE(deliveryStatus); // 부르미에게_새로운_위치정보_전달_SSE사용()
    }

    private String doPickupFinishByDreami(DeliveryStatus deliveryStatus, UUID dreamiId, String photoKey) {
        // 이 주문에 배정된 드리미 본인만 픽업 완료를 처리할 수 있다
        if (!deliveryStatus.dreamiId().equals(dreamiId)) {
            throw new BusinessException(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        }

        // 부르미가 취소 눌렀는데 드리미는 아직 인지 못하고 픽업 완료를 누름
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 관리자가 취소 했는데 드리미는 아직 인지 못하고 픽업 완료를 누름
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_관리자의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 정상 "배달중"이면 이미 픽업 완료를 호출한 상황(버튼 연타 등)
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            throw new BusinessException(DeliveryErrorCode.STEP_ALREADY_VERIFIED);
        }

        // 드리미가 픽업 완료를 요청했는데 드리미가 취소하는건 안될듯?
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 픽업을 완료 하려고 요청했는데 이미 배달이 완료된 건이다?? -> 말이 안됨
        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        }

        // 여기까지 왔으면 이제 "픽업중_정상" 상태만 남음
        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상

        if (!hasPickupPhoto(deliveryStatus.orderId(), dreamiId, photoKey)) { // 사진이_없는경우
            throw new BusinessException(DeliveryErrorCode.PICKUP_PHOTO_MISSING);
        }

        deliveryStatus.setStatus(DELIVERING); // 배달중_정상
        alarmBoormiDeliveringBySSE(deliveryStatus); // 부르미에게_배달중_상태로_바뀌었다고_전달_SSE사용()
        return "픽업 완료";
    }

    private String doCancelByDreami(DeliveryStatus deliveryStatus) {
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 이미 배달 중 상태라면, 배달 화면에서 고객센터에 연락하거나 화면 갱신 후 시도
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            throw new BusinessException(DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY);
        }

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_관리자의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 이미 드리미가 취소 요청을 보낸 상황(버튼 연타 등)
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 배달 완료 상태에서는 픽업 취소 호출이 될 수가 없음
        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        }

        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상
        deliveryStatus.setStatus(PICKUP_CANCELLED_BY_DREAMI); // 픽업중_드리미의_취소
        alarmBoormiDreamiCancelBySSE(deliveryStatus); // 부르미에게_픽업중에_드리미가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doCancelByBoormi(DeliveryStatus deliveryStatus) {
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_관리자의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 이미 배달 중 상태라면, 배달 화면에서 고객센터에 연락하거나 화면 갱신 후 시도
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            throw new BusinessException(DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY);
        }

        // 배달 완료 상태에서는 픽업 취소 호출이 될 수가 없음
        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        }

        // 이미 부르미가 취소 요청을 보낸 상황에서 중복 호출될 수 없음
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상
        deliveryStatus.setStatus(PICKUP_CANCELLED_BY_BOORMI); // 픽업중_부르미의_취소
        alarmDreamiBoormiCancelBySSE(deliveryStatus); // 드리미에게_부르미가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doCancelByAdmin(DeliveryStatus deliveryStatus) {
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI) { // 픽업중_부르미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        // 이미 배달 중 상태라면 취소할 수 없음
        if (deliveryStatus.status() == DELIVERING) { // 배달중_정상
            throw new BusinessException(DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY);
        }

        // 배달 완료 상태에서는 픽업 취소 호출이 될 수가 없음
        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        }

        // 이미 관리자가 취소한 상황에서 중복 호출될 수 없음
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_관리자의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        assert deliveryStatus.status() == PICKUP_NORMAL; // 픽업중_정상
        deliveryStatus.setStatus(PICKUP_CANCELLED_BY_ADMIN); // 픽업중_관리자의_취소

        alarmBoormiAdminCancelBySSE(deliveryStatus); // 부르미에게_관리자가_취소했다고_전달_SSE사용()
        alarmDreamiAdminCancelBySSE(deliveryStatus); // 드리미에게_관리자가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doFinishDelivery(DeliveryStatus deliveryStatus, UUID dreamiId, String photoKey) {
        // 이 주문에 배정된 드리미 본인만 배달 완료를 처리할 수 있다
        if (!deliveryStatus.dreamiId().equals(dreamiId)) {
            throw new BusinessException(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        }

        // 아마 드리미가 픽업하자마자 바로 배달완료 처리요청하지 않는 이상 없을듯?
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_BOORMI
                || deliveryStatus.status() == PICKUP_CANCELLED_BY_ADMIN) { // 픽업중_부르미의_취소 || 픽업중_관리자의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        if (deliveryStatus.status() == DELIVERED) { // 배달_완료
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        }

        // 픽업 중에 배달 완료 요청이 온다?
        if (deliveryStatus.status() == PICKUP_NORMAL) { // 픽업중_정상
            throw new BusinessException(DeliveryErrorCode.PICKUP_NOT_COMPLETED);
        }

        // 드리미가 취소 했는데 그 이후에 배달을 완료할 수는 없음
        if (deliveryStatus.status() == PICKUP_CANCELLED_BY_DREAMI) { // 픽업중_드리미의_취소
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
        }

        assert deliveryStatus.status() == DELIVERING; // 배달중_정상

        if (!hasDeliveryPhoto(deliveryStatus.orderId(), dreamiId, photoKey)) { // 사진이없을때
            throw new BusinessException(DeliveryErrorCode.DELIVERY_COMPLETION_PHOTO_MISSING);
        }

        deliveryStatus.setStatus(DELIVERED); // 배달_완료
        alarmBoormiDeliveredBySSE(deliveryStatus); // 부르미에게_배달완료라고_전달_SSE로()
        return "드리미에게_완료";
    }

    // ===== SSE 알림 =====
    // 모두 락 안에서 호출된다. 불변 스냅샷(DeliveryStatusResponseDto)을 만들어 SseService로 넘기므로
    // async 전송 스레드는 가변 DeliveryStatus를 건드리지 않는다(추가 동시성 처리 불필요).

    private void alarmDreamiBoormiCancelBySSE(DeliveryStatus ds) {
        sseService.send(ds.dreamiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(ds, "고객이 주문을 취소했습니다"));
    }

    private void alarmDreamiAdminCancelBySSE(DeliveryStatus ds) {
        sseService.send(ds.dreamiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(ds, "관리자가 배달을 취소했습니다"));
    }

    private void alarmBoormiLocationBySSE(DeliveryStatus ds) {
        sseService.send(ds.boormiId(), DeliveryEventType.DELIVERY_LOCATION,
                DeliveryStatusResponseDto.from(ds, "위치 갱신됨"));
    }

    private void alarmBoormiDeliveringBySSE(DeliveryStatus ds) {
        sseService.send(ds.boormiId(), DeliveryEventType.DELIVERY_DELIVERING,
                DeliveryStatusResponseDto.from(ds, "배달이 시작되었습니다"));
    }

    private void alarmBoormiDreamiCancelBySSE(DeliveryStatus ds) {
        sseService.send(ds.boormiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(ds, "드리미가 픽업을 취소했습니다"));
    }

    private void alarmBoormiAdminCancelBySSE(DeliveryStatus ds) {
        sseService.send(ds.boormiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(ds, "관리자가 배달을 취소했습니다"));
    }

    private void alarmBoormiDeliveredBySSE(DeliveryStatus ds) {
        sseService.send(ds.boormiId(), DeliveryEventType.DELIVERY_COMPLETED,
                DeliveryStatusResponseDto.from(ds, "배달이 완료되었습니다"));
    }

    // ===== 사진 확인 (upload 도메인 연동) =====
    // 드리미가 자기 세션으로 발급받아 업로드한 key인지(소유권) 확인한 뒤, 그 파일이 S3에 실제 존재하는지 검사한다.

    private boolean hasPickupPhoto(UUID orderId, UUID dreamiId, String photoKey) {
        // 본인이, 이 주문에 대해, 픽업 인증사진 용도로 발급받은 key가 아니면 KEY_OWNER_MISMATCH
        uploadSessionService.validateScope(UploadPurpose.PICKUP_CERTIFICATION_IMAGE, dreamiId, orderId, photoKey);
        return s3PresignService.isFileUploaded(photoKey);
    }

    private boolean hasDeliveryPhoto(UUID orderId, UUID dreamiId, String photoKey) {
        // 본인이, 이 주문에 대해, 배달완료 인증사진 용도로 발급받은 key가 아니면 KEY_OWNER_MISMATCH
        uploadSessionService.validateScope(UploadPurpose.DELIVERY_CERTIFICATION_IMAGE, dreamiId, orderId, photoKey);
        return s3PresignService.isFileUploaded(photoKey);
    }
}
