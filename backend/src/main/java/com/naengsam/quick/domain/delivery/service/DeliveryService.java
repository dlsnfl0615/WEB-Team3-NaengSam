package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.user.exception.UserErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.service.DirectionsService;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.delivery.dto.DeliveryCompletionDto;
import com.naengsam.quick.domain.delivery.dto.DeliveryContactDto;
import com.naengsam.quick.domain.delivery.dto.DeliveryDetailResponseDto;
import com.naengsam.quick.domain.delivery.dto.PickupPhotoDto;
import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationResponseDto;
import com.naengsam.quick.domain.delivery.dto.RoutePointDto;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.entity.DeliveryCertification;
import com.naengsam.quick.domain.delivery.entity.PickupCertification;
import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.delivery.event.DeliveryNotificationEvent;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.delivery.repository.DeliveryCertificationRepository;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.domain.delivery.repository.PickupCertificationRepository;
import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.service.PaymentService;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.domain.upload.service.UploadSessionService;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.*;

/**
 * 배달 한 건의 상태 전이를 담당한다. 공개 메서드는 동기 요청으로 호출되며, 요청 스레드에서 그대로 실행된다.
 * 상태 검증+변경(check-then-act)은 트랜잭션 안에서 해당 주문의 Delivery 행을 비관적 쓰기 락으로 조회해 주문 단위로만 직렬화한다.
 * 서로 다른 주문은 서로 다른 행이라 완전히 병렬로 처리된다.
 *
 * <p>알림(SSE)은 전이와 같은 트랜잭션 안에서 불변 스냅샷(DeliveryStatusResponseDto)을 담은 {@link DeliveryNotificationEvent}만
 * 발행한다. 실제 NotificationService.notify 호출은 트랜잭션이 커밋된 뒤에만({@code sendAfterCommit}, AFTER_COMMIT) 일어나므로,
 * 롤백된 전이에 대해서는 알림이 나가지 않는다. IN_APP 채널은 SseService가 단일 가상 스레드로 async 오프로딩하므로
 * "처리 순서 == 알림 순서"가 보장되고 호출 스레드는 막히지 않는다.
 *
 * <p>부르미·드리미 모두 상태 변경은 SSE로 전달받는다. updateDreamiLocation은 드리미가 5~10초마다 호출해
 * 위치만 전송하는 용도이며(응답은 ack), 취소/완료된 주문에 대한 가드 예외는 폴링 중단 신호로만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final int LOCATION_SCALE = 8;

    // 연락처를 더 내려줄 이유가 없는 종료 상태들. 프론트가 추적 불가로 판정하는 집합(getUntrackableDeliveryNotice)과 같다.
    private static final Set<DeliveryCd> CONTACT_CLOSED_STATUSES = EnumSet.of(
            DELIVERED, PICKUP_CANCELLED_BY_BOORMI, PICKUP_CANCELLED_BY_DREAMI, PICKUP_CANCELLED_BY_ADMIN,
            RETURNED, TERMINATED);

    private final DeliveryRepository deliveryRepository;
    private final PickupCertificationRepository pickupCertificationRepository;
    private final DeliveryCertificationRepository deliveryCertificationRepository;
    private final NotificationService notificationService;
    private final UploadSessionService uploadSessionService;
    private final UserService userService;
    private final OrderService orderService;
    private final DreamiRepository dreamiRepository;
    private final BoormiRepository boormiRepository;
    private final S3PresignService s3PresignService;
    private final PaymentService paymentService;
    private final DirectionsService directionsService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final DreamiOfflineDetector dreamiOfflineDetector;

    // ===== 배달 시작 (진입점) =====

    // 매칭이 확정된 주문의 배달을 시작한다. 초기 상태 PICKUP_NORMAL로 Delivery를 만들어 저장해,
    // 이후 상태 전이 요청들이 findByOrderId(orderId)로 이 주문을 찾을 수 있게 한다.
    // 호출부(매칭 확정 훅)는 매칭 도메인 담당이며 여기서는 진입점만 제공한다. boormiId는 호출부에서 넘겨받는다.
    @Transactional
    public void startDelivery(UUID orderId, UUID dreamiId, UUID boormiId) {
        Orders order = orderService.getOrder(orderId);

        if (order.getOrderCd() != OrderCd.IN_PROGRESS) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_START_NOT_ALLOWED);
        }
        if (!userService.getUserInfo(dreamiId).isDreami()) { // 배달자는 활성 드리미여야 함
            throw new BusinessException(DeliveryErrorCode.DREAMI_NOT_ACTIVATED);
        }
        Delivery delivery = deliveryRepository.save(Delivery.create(orderId, dreamiId, boormiId));
        alarmBoormiDeliveryStartedBySSE(delivery); // 부르미에게_배달이_시작됐다고_전달_SSE사용()
        alarmDreamiDeliveryStartedBySSE(delivery); // 드리미에게_배달이_시작됐다고_전달_SSE사용()
    }

    // 배달 추적 화면용 상세 조회. 출발지·도착지 좌표(Orders)와 현재 드리미 위치·상태(Delivery)를 조합해 반환한다.
    // 이 주문의 부르미 또는 배정된 드리미만 볼 수 있다(그 외 403). dreamiId가 null이어도 equals(null)==false라 안전하다.
    @Transactional(readOnly = true)
    public DeliveryDetailResponseDto getDeliveryDetail(UUID orderId, UUID userId) {
        Delivery delivery = deliveryRepository.findByOrderIdWithoutLock(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        Orders order = orderService.getOrder(orderId);

        if (!userId.equals(delivery.getBoormiId()) && !userId.equals(delivery.getDreamiId())) {
            throw new BusinessException(AuthErrorCode.NOT_RESOURCE_OWNER);
        }

        // 픽업 후 지도용 픽업지→도착지 경로(Orders)와 픽업 전 지도용 드리미→픽업지 경로(Delivery)를 함께 내려준다.
        // 드리미 연결 상태도 함께 담는다 — 화면을 다시 열었을 때 SSE 이벤트를 기다리지 않고 즉시 복원하기 위함이며,
        // 부르미가 끊긴 동안 유실된 오프라인 통보를 되찾는 경로이기도 하다.
        return DeliveryDetailResponseDto.from(delivery, order,
                resolveItemPhotoUrl(order.getImageKey()),
                parseRoutePath(order.getRoutePath()), parseRoutePath(delivery.getRoutePath()),
                dreamiOfflineDetector.isOffline(delivery),
                dreamiOfflineDetector.secondsSinceLastLocationOrNull(delivery));
    }

    // 배달 추적 화면의 픽업사진 버튼용 조회. 픽업 인증 사진은 배달 도중(픽업 완료 시점)에야 생기는 값이라
    // getDeliveryDetail의 스냅샷에 실어두면 SSE로 픽업 완료를 알린 뒤에도 화면이 새로고침 전까지 stale하게 "사진 없음"을
    // 보여준다. 그래서 버튼을 누른 시점에 이 메서드로 그때그때 조회한다. 소유권 검증은 getDeliveryDetail과 동일하다.
    @Transactional(readOnly = true)
    public PickupPhotoDto getPickupPhoto(UUID orderId, UUID userId) {
        Delivery delivery = deliveryRepository.findByOrderIdWithoutLock(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!userId.equals(delivery.getBoormiId()) && !userId.equals(delivery.getDreamiId())) {
            throw new BusinessException(AuthErrorCode.NOT_RESOURCE_OWNER);
        }

        return new PickupPhotoDto(resolvePickupPhotoUrl(orderId));
    }

    // 배달 완료 화면용 요약 조회. 위치·경로 없이 완료 후에만 의미 있는 정산·담당 드리미·소요시간만 담는다.
    // 소유권 검증은 getDeliveryDetail과 동일하다(이 주문의 부르미 또는 배정된 드리미만 조회 가능).
    @Transactional(readOnly = true)
    public DeliveryCompletionDto getDeliveryCompletion(UUID orderId, UUID userId) {
        Delivery delivery = deliveryRepository.findByOrderIdWithoutLock(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        Orders order = orderService.getOrder(orderId);

        if (!userId.equals(delivery.getBoormiId()) && !userId.equals(delivery.getDreamiId())) {
            throw new BusinessException(AuthErrorCode.NOT_RESOURCE_OWNER);
        }

        // 담당 드리미 이름은 BOORMI 테이블에서, 평점은 DREAMI 테이블에서 가져온다(dreamiId == boormiId).
        Dreami dreami = dreamiRepository.findById(delivery.getDreamiId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        String dreamiName = boormiRepository.findById(delivery.getDreamiId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .getName();
        String boormiName = boormiRepository.findById(delivery.getBoormiId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .getName();
        String deliveryPhotoUrl = resolveDeliveryPhotoUrl(delivery.getDeliveryId());
        // 평가 대상 문구는 클라이언트가 URL로 정하게 두면 조작 가능하므로, 로그인 세션 기반으로 서버가 판정해 내려준다.
        boolean viewerIsDreami = userId.equals(delivery.getDreamiId());

        return DeliveryCompletionDto.from(delivery, order, dreamiName, dreami.getDreamiAvgScore(), boormiName,
                deliveryPhotoUrl, viewerIsDreami);
    }

    // 배달 화면에서 '연락하기'를 눌렀을 때만 호출되는 상대방 연락처 조회.
    // 전화번호는 개인정보라 추적 상세(getDeliveryDetail)에 싣지 않는다 — 그쪽은 SSE 재연결마다 재조회되므로
    // 화면이 열려 있는 내내 번호가 오간다. 여기서는 당사자에게, 배달이 진행 중일 때만 내려준다.
    @Transactional(readOnly = true)
    public DeliveryContactDto getDeliveryContact(UUID orderId, UUID userId) {
        Delivery delivery = deliveryRepository.findByOrderIdWithoutLock(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (!userId.equals(delivery.getBoormiId()) && !userId.equals(delivery.getDreamiId())) {
            throw new BusinessException(AuthErrorCode.NOT_RESOURCE_OWNER);
        }
        if (CONTACT_CLOSED_STATUSES.contains(delivery.getDeliveryCd())) {
            throw new BusinessException(DeliveryErrorCode.CONTACT_NOT_AVAILABLE);
        }

        // 조회자가 어느 쪽인지는 클라이언트 파라미터가 아니라 로그인 세션으로 서버가 판정한다(getDeliveryCompletion과 같은 이유).
        boolean viewerIsDreami = userId.equals(delivery.getDreamiId());
        UUID counterpartId = viewerIsDreami ? delivery.getBoormiId() : delivery.getDreamiId();
        // 드리미의 이름·전화번호도 BOORMI 테이블에 있다(dreamiId == boormiId).
        Boormi counterpart = boormiRepository.findById(counterpartId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return DeliveryContactDto.from(counterpart, viewerIsDreami);
    }

    // 배송 완료 인증 사진 URL을 조회한다. 완료 전이라 인증 사진이 아직 없으면(레코드 없음) 조회하지 않고,
    // 있어도 S3 조회 자체가 실패하면(보존 정책 삭제, 스토리지 장애 등) resolveDownloadUrl이 null로 degrade해
    // 완료 요약 전체를 실패시키지 않는다.
    private String resolveDeliveryPhotoUrl(UUID deliveryId) {
        return deliveryCertificationRepository.findByDeliveryId(deliveryId)
                .map(certification -> s3PresignService.resolveDownloadUrl(certification.getImageKey()))
                .orElse(null);
    }

    // 부르미가 주문 접수 시 등록한 물품 사진 URL을 조회한다. 사진을 안 올렸으면(imageKey 없음) 조회하지 않는다.
    // 부르미가 주문 접수 시 물품 사진을 등록하지 않아도 되므로 image key가 null일 수 있음
    private String resolveItemPhotoUrl(String imageKey) {
        return imageKey == null ? null : s3PresignService.resolveDownloadUrl(imageKey);
    }

    // 드리미가 찍은 픽업 인증 사진 URL을 조회한다. 픽업 전이라 아직 없으면(레코드 없음) 조회하지 않는다.
    private String resolvePickupPhotoUrl(UUID orderId) {
        return pickupCertificationRepository.findByOrderId(orderId)
                .map(certification -> s3PresignService.resolveDownloadUrl(certification.getImageKey()))
                .orElse(null);
    }

    // 주문에 저장된 추천 이동경로 JSON을 좌표 목록으로 복원한다. 값이 없거나 손상됐으면 빈 목록(지도는 핀만 표시).
    private List<RoutePointDto> parseRoutePath(String routePathJson) {
        if (routePathJson == null || routePathJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(routePathJson, new TypeReference<List<RoutePointDto>>() {
            });
        } catch (JacksonException e) {
            log.warn("주문 route_path 역직렬화 실패 — 경로 없이 응답한다", e);
            return Collections.emptyList();
        }
    }

    // ===== 공개 메서드 (동기 요청) — 주문 단위 락 안에서 상태 전이를 실행하고 결과를 돌려준다 =====

    // 드리미 위치 정보를 전달 (이 메소드를 5~10초마다 드리미가 호출해야함). 위치를 갱신하고,
    // 첫 위치면 방금 계산·저장된 '드리미→픽업지' 경로와 배송완료예상시간을, 이후엔 이미 저장된 값을 그대로 응답에 담아 돌려준다.
    // (프론트가 이 응답만으로 지도·배송완료예상시간을 갱신할 수 있어 별도 재조회가 필요 없다.)
    @Transactional
    public DreamiLocationResponseDto updateDreamiLocation(UUID orderId, DreamiLocationRequest location) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        doUpdateDreamiLocation(delivery, location);

        // 경로(좌표 배열)는 매 5초마다 중복 전송하지 않도록, 클라이언트가 요청에서 명시적으로 요청할 때만(includeRoute) 실어 보낸다.
        // 아직 경로가 없는 드리미는 true로 계속 요청하다가, 한 번 받으면 false로 바꿔 이후엔 위치만 보낸다.
        // 하위호환: includeRoute가 없으면(null) 예전처럼 경로를 담아 보낸다.
        boolean includeRoute =
                location == null || location.includeRoute() == null || location.includeRoute();

        return new DreamiLocationResponseDto(
                includeRoute ? parseRoutePath(delivery.getRoutePath()) : null,
                includeRoute ? delivery.getEstimatedCompletionDtm() : null);
    }

    // 픽업 완료 (드리미가 업로드한 픽업 인증 사진 key를 함께 받아 검증한다)
    @Transactional
    public DeliveryStatusResponseDto pickupFinishByDreami(UUID orderId, UUID dreamiId, String photoKey) {
        return transition(orderId, delivery -> {
            return doPickupFinishByDreami(delivery, dreamiId, photoKey);
        });
    }

    // "픽업" 과정에서 드리미의 취소 (요청자가 이 배달에 배정된 드리미 본인인지 검증한다)
    @Transactional
    public DeliveryStatusResponseDto cancelByDreami(UUID orderId, UUID dreamiId) {
        return transition(orderId, delivery -> {
            return doCancelByDreami(delivery, dreamiId);
        });
    }

    // 픽업 중에 부르미가 취소 (요청자가 이 주문을 접수한 부르미 본인인지 검증한다)
    @Transactional
    public DeliveryStatusResponseDto cancelByBoormi(UUID orderId, UUID boormiId) {
        return transition(orderId, delivery -> {
            return doCancelByBoormi(delivery, boormiId);
        });
    }

    // 픽업 중에 관리자가 취소
    @Transactional
    public DeliveryStatusResponseDto cancelByAdmin(UUID orderId) {
        return transition(orderId, this::doCancelByAdmin);
    }

    // 드리미가 "배달" 완료 (픽업 아님!!) — 배달 완료 인증 사진 key를 함께 받아 검증한다
    @Transactional
    public DeliveryStatusResponseDto finishDelivery(UUID orderId, UUID dreamiId, String photoKey) {
        return transition(orderId, delivery -> {
            return doFinishDelivery(delivery, dreamiId, photoKey);
        });
    }

    // ===== 주문 단위 직렬화 =====

    // 해당 주문의 Delivery 행을 비관적 쓰기 락으로 조회해 check-then-act를 원자적으로 실행한다.
    // 같은 주문 = 같은 행 락 = 직렬화, 다른 주문 = 다른 행 = 병렬.
    // logic이 상태를 바꾼 뒤(여전히 트랜잭션 안)의 스냅샷으로 응답 DTO를 만들어 status·message 일관성을 보장한다.
    private DeliveryStatusResponseDto transition(UUID orderId, Function<Delivery, String> logic) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        String message = logic.apply(delivery);
        return DeliveryStatusResponseDto.from(delivery, message);
    }

    // ===== 실제 상태 전이 로직 (주문 락 안에서 실행) =====

    // 위치를 갱신하고 부르미에게 SSE로 전달한다. 첫 위치면 '드리미→픽업지' 경로·배송완료예상시간을 1회 계산해 저장한다.
    // 취소/완료 상태에 대한 예외는 드리미 폴링을 멈추게 하는 신호로 남긴다(취소 알림 자체는 취소 시점에 이미 드리미에게 SSE로 push된다).
    private void doUpdateDreamiLocation(Delivery delivery, DreamiLocationRequest location) {

        // 이미 완료되거나 취소된 주문이면 위치를 더 이상 갱신하지 않음
        // 잘못된 비즈니스 상황에서는 예외처리
        // 위치 갱신이 필요한 모든 상황에서는 위치 갱신
        DeliveryCd deliveryCd = delivery.getDeliveryCd();
        boolean isValid = switch (deliveryCd) {
            case PICKUP_NORMAL, DELIVERING, PICKUP_DELAYED, PARTNER_HANDOFF_PENDING, TRANSFERRED_TO_PARTNER,
                 RETURNING -> true;
            case PICKUP_CANCELLED_BY_BOORMI, PICKUP_CANCELLED_BY_DREAMI, PICKUP_CANCELLED_BY_ADMIN ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            case DELIVERED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
            case RETURNED, TERMINATED -> false;
        };

        if (!isValid) return;

        if (location == null || location.latitude() == null || location.longitude() == null) {
            throw new BusinessException(DeliveryErrorCode.LOCATION_COLLECTION_FAILED);
        }

        BigDecimal latitude = location.latitude().setScale(LOCATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal longitude = location.longitude().setScale(LOCATION_SCALE, RoundingMode.HALF_UP);
        delivery.updateLocation(latitude, longitude); // 위치정보_수정()
        maybeComputePickupRoute(delivery, latitude, longitude); // 첫 위치 전송 때 '드리미→픽업지' 경로·배송완료예상시간 1회 계산
        alarmBoormiLocationBySSE(delivery); // 부르미에게_새로운_위치정보_전달_SSE사용()
    }

    // 드리미의 첫 위치가 들어온 픽업 단계에서 '드리미→픽업지' 카카오 도보 경로와 배송완료예상시간을 1회만 계산해 저장한다.
    // routePath가 이미 있거나 픽업 단계가 아니면 아무것도 하지 않는다. 카카오 실패는 위치 갱신을 막지 않고 다음 위치 전송 때 재시도한다.
    private void maybeComputePickupRoute(Delivery delivery, BigDecimal latitude, BigDecimal longitude) {
        if (delivery.getRoutePath() != null) {
            return;
        }
        DeliveryCd deliveryCd = delivery.getDeliveryCd();
        if (deliveryCd != PICKUP_NORMAL && deliveryCd != PICKUP_DELAYED) {
            return;
        }
        try {
            Orders order = orderService.getOrder(delivery.getOrderId());
            GeoPoint dreami = new GeoPoint(latitude, longitude);
            GeoPoint pickup = new GeoPoint(order.getOriginLatitude(), order.getOriginLongitude());

            KakaoDirectionsResponseDto.Route route = directionsService.getRoute(dreami, pickup);
            String routePathJson = objectMapper.writeValueAsString(RoutePointDto.from(route));

            // 드리미→픽업지 소요(분, BoormiService와 동일 공식) + 주문의 픽업지→도착지 delivery_eta(분)를 현재 시각에 더한다.
            int pickupEtaMinutes = (int) Math.ceil(route.properties().totalTime() / 60.0);
            LocalDateTime estimatedCompletion =
                    LocalDateTime.now().plusMinutes((long) pickupEtaMinutes + order.getDeliveryEta());

            delivery.applyPickupRoute(routePathJson, estimatedCompletion);
        } catch (BusinessException | JacksonException e) {
            log.warn("드리미→픽업지 경로·배송완료예상시간 계산 실패 — 다음 위치 전송 때 재시도", e);
        }
    }

    private String doPickupFinishByDreami(Delivery delivery, UUID dreamiId, String photoKey) {
        // 이 주문에 배정된 드리미 본인만 픽업 완료를 처리할 수 있다
        if (!delivery.getDreamiId().equals(dreamiId)) {
            throw new BusinessException(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        }

        // 부르미가 취소 눌렀는데 드리미는 아직 인지 못하고 픽업 완료를 누름
        // 관리자가 취소 했는데 드리미는 아직 인지 못하고 픽업 완료를 누름 -> 사용자에게 메시지
        // 드리미가 픽업 완료를 요청했는데 드리미가 취소하는건 안될듯?
        // 정상 "배달중"이면 이미 픽업 완료를 호출한 상황(버튼 연타 등)
        // 픽업을 완료 하려고 요청했는데 이미 배달이 완료된 건이다?? -> 말이 안됨
        DeliveryCd deliveryCd = delivery.getDeliveryCd();
        boolean isValid = switch (deliveryCd) {
            case PICKUP_NORMAL, PICKUP_DELAYED -> true;
            case PICKUP_CANCELLED_BY_BOORMI, PICKUP_CANCELLED_BY_DREAMI, PICKUP_CANCELLED_BY_ADMIN ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            case DELIVERING -> throw new BusinessException(DeliveryErrorCode.STEP_ALREADY_VERIFIED);
            case DELIVERED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
            // 파트너로 인계 대기 중에는 픽업 완료 누를 수 없음
            case PARTNER_HANDOFF_PENDING ->
                    throw new BusinessException(DeliveryErrorCode.PICKUP_NOT_COMPLETED_HANDOFF_PENDING);
            case TRANSFERRED_TO_PARTNER ->
                    throw new BusinessException(DeliveryErrorCode.PICKUP_NOT_COMPLETED_HANDOFF_TRANSFERRED);
            case RETURNING -> throw new BusinessException(DeliveryErrorCode.PICKUP_NOT_COMPLETED_RETURNING);
            case RETURNED -> throw new BusinessException(DeliveryErrorCode.PICKUP_NOT_COMPLETED_RETURNED);
            case TERMINATED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_TERMINATED);
        };

        if (!isValid) return "픽업 취소 실패";


        // 본인이, 이 주문에 대해, 픽업 인증사진 용도로 발급받은 key인지 확인하고, 업로드 안 됐으면 FILE_NOT_FOUND
        uploadSessionService.checkUpload(UploadPurpose.PICKUP_CERTIFICATION_IMAGE, dreamiId, delivery.getOrderId(),
                photoKey);

        delivery.markDelivering(); // 배달중_정상
        // 비대면 픽업 인증 행 저장 (submittedDtm은 markDelivering이 기록한 pickedUpDtm 재사용)
        pickupCertificationRepository.save(
                PickupCertification.create(photoKey, delivery.getPickedUpDtm(), delivery.getOrderId()));
        alarmBoormiDeliveringBySSE(delivery); // 부르미에게_배달중_상태로_바뀌었다고_전달_SSE사용()
        return "픽업 완료";
    }

    private String doCancelByDreami(Delivery delivery, UUID dreamiId) {
        // 이 배달에 배정된 드리미 본인만 취소할 수 있다.
        if (!delivery.getDreamiId().equals(dreamiId)) {
            throw new BusinessException(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        }

        // 픽업 단계(정상/지연)에서만 드리미가 취소할 수 있다.
        // 이미 취소된 건(부르미/드리미/관리자)은 중복 취소, 배달 중/완료/종료·파트너 인계·반송 단계는 취소 불가
        DeliveryCd deliveryCd = delivery.getDeliveryCd();
        boolean isValid = switch (deliveryCd) {
            case PICKUP_NORMAL, PICKUP_DELAYED -> true;
            case PICKUP_CANCELLED_BY_BOORMI, PICKUP_CANCELLED_BY_DREAMI, PICKUP_CANCELLED_BY_ADMIN ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            // 배달 중이면 배달 화면에서 고객센터 연락하거나 화면 갱신 후 시도
            case DELIVERING -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY);
            case DELIVERED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
            case PARTNER_HANDOFF_PENDING ->
                    throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_HANDOFF_PENDING);
            case TRANSFERRED_TO_PARTNER ->
                    throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_HANDOFF_TRANSFERRED);
            case RETURNING -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_RETURNING);
            case RETURNED -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_RETURNED);
            case TERMINATED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_TERMINATED);
        };

        if (!isValid) return "픽업 취소 실패";

        delivery.cancelBy(PICKUP_CANCELLED_BY_DREAMI); // 픽업중_드리미의_취소
        orderService.cancel(delivery.getOrderId(), CancelerCd.DREAMI); // 주문도 취소 상태로 전이 + 취소 이력 저장
        paymentService.refundByPoint(delivery.getOrderId()); // 결제 포인트 전액 환불 (SSE 알림 전에 DB 작업을 끝낸다)
        alarmBoormiDreamiCancelBySSE(delivery); // 부르미에게_픽업중에_드리미가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doCancelByBoormi(Delivery delivery, UUID boormiId) {
        // 이 주문을 접수한 부르미 본인만 취소할 수 있다.
        if (!delivery.getBoormiId().equals(boormiId)) {
            throw new BusinessException(DeliveryErrorCode.NOT_ORDER_BOORMI);
        }

        // 픽업 단계(정상/지연)에서만 부르미가 취소할 수 있다.
        // 이미 취소된 건(부르미/드리미/관리자)은 중복 취소, 배달 중/완료/종료·파트너 인계·반송 단계는 취소 불가
        DeliveryCd deliveryCd = delivery.getDeliveryCd();
        boolean isValid = switch (deliveryCd) {
            case PICKUP_NORMAL, PICKUP_DELAYED -> true;
            case PICKUP_CANCELLED_BY_BOORMI, PICKUP_CANCELLED_BY_DREAMI, PICKUP_CANCELLED_BY_ADMIN ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            // 배달 중이면 배달 화면에서 고객센터 연락하거나 화면 갱신 후 시도
            case DELIVERING -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY);
            case DELIVERED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
            case PARTNER_HANDOFF_PENDING ->
                    throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_HANDOFF_PENDING);
            case TRANSFERRED_TO_PARTNER ->
                    throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_HANDOFF_TRANSFERRED);
            case RETURNING -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_RETURNING);
            case RETURNED -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_RETURNED);
            case TERMINATED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_TERMINATED);
        };

        if (!isValid) return "픽업 취소 실패";

        delivery.cancelBy(PICKUP_CANCELLED_BY_BOORMI); // 픽업중_부르미의_취소
        orderService.cancel(delivery.getOrderId(), CancelerCd.BOORMI); // 주문도 취소 상태로 전이 + 취소 이력 저장
        paymentService.refundByPoint(delivery.getOrderId()); // 결제 포인트 전액 환불 (SSE 알림 전에 DB 작업을 끝낸다)
        alarmDreamiBoormiCancelBySSE(delivery); // 드리미에게_부르미가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doCancelByAdmin(Delivery delivery) {
        // 픽업 단계(정상/지연)에서만 관리자가 취소할 수 있다.
        // 이미 취소된 건(부르미/드리미/관리자)은 중복 취소, 배달 중/완료/종료·파트너 인계·반송 단계는 취소 불가
        DeliveryCd deliveryCd = delivery.getDeliveryCd();
        boolean isValid = switch (deliveryCd) {
            case PICKUP_NORMAL, PICKUP_DELAYED -> true;
            case PICKUP_CANCELLED_BY_BOORMI, PICKUP_CANCELLED_BY_DREAMI, PICKUP_CANCELLED_BY_ADMIN ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            case DELIVERING -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_RESTRICTED_DURING_DELIVERY);
            case DELIVERED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
            case PARTNER_HANDOFF_PENDING ->
                    throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_HANDOFF_PENDING);
            case TRANSFERRED_TO_PARTNER ->
                    throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_HANDOFF_TRANSFERRED);
            case RETURNING -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_RETURNING);
            case RETURNED -> throw new BusinessException(DeliveryErrorCode.CANCELLATION_NOT_ALLOWED_RETURNED);
            case TERMINATED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_TERMINATED);
        };

        if (!isValid) return "픽업 취소 실패";

        delivery.cancelBy(PICKUP_CANCELLED_BY_ADMIN); // 픽업중_관리자의_취소
        orderService.cancel(delivery.getOrderId(), CancelerCd.ADMIN); // 주문도 취소 상태로 전이 + 취소 이력 저장
        paymentService.refundByPoint(delivery.getOrderId()); // 결제 포인트 전액 환불 (SSE 알림 전에 DB 작업을 끝낸다)
        alarmBoormiAdminCancelBySSE(delivery); // 부르미에게_관리자가_취소했다고_전달_SSE사용()
        alarmDreamiAdminCancelBySSE(delivery); // 드리미에게_관리자가_취소했다고_전달_SSE사용()
        return "픽업 취소 완료";
    }

    private String doFinishDelivery(Delivery delivery, UUID dreamiId, String photoKey) {
        // 이 주문에 배정된 드리미 본인만 배달 완료를 처리할 수 있다
        if (!delivery.getDreamiId().equals(dreamiId)) {
            throw new BusinessException(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        }

        // "배달중"에서만 배달 완료를 처리할 수 있다.
        // 픽업 단계(정상/지연)는 픽업 미완료, 취소·완료·종료·파트너 인계·반송 단계는 완료 처리 불가
        DeliveryCd deliveryCd = delivery.getDeliveryCd();
        boolean isValid = switch (deliveryCd) {
            case DELIVERING -> true;
            // 픽업 중에 배달 완료 요청이 온 경우 (픽업하자마자 바로 완료 요청하지 않는 이상 드묾)
            case PICKUP_NORMAL, PICKUP_DELAYED ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_COMPLETION_NOT_ALLOWED_BEFORE_PICKUP);
            case PICKUP_CANCELLED_BY_BOORMI, PICKUP_CANCELLED_BY_DREAMI, PICKUP_CANCELLED_BY_ADMIN ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            case DELIVERED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
            case PARTNER_HANDOFF_PENDING ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_COMPLETION_NOT_ALLOWED_HANDOFF_PENDING);
            case TRANSFERRED_TO_PARTNER ->
                    throw new BusinessException(DeliveryErrorCode.DELIVERY_COMPLETION_NOT_ALLOWED_HANDOFF_TRANSFERRED);
            case RETURNING -> throw new BusinessException(DeliveryErrorCode.DELIVERY_COMPLETION_NOT_ALLOWED_RETURNING);
            case RETURNED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_COMPLETION_NOT_ALLOWED_RETURNED);
            case TERMINATED -> throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_TERMINATED);
        };

        if (!isValid) return "배달 완료 실패";

        // 본인이, 이 주문에 대해, 배달완료 인증사진 용도로 발급받은 key인지 확인하고, 업로드 안 됐으면 FILE_NOT_FOUND
        uploadSessionService.checkUpload(UploadPurpose.DELIVERY_CERTIFICATION_IMAGE, dreamiId, delivery.getOrderId(),
                photoKey);

        delivery.markDelivered(); // 배달_완료
        orderService.complete(delivery.getOrderId()); // 주문도 완료 상태로 전이
        // 배달이 끝나야 지급이 확정된다: 결제 거래를 PAID 로 전이하고 드리미 머니 지갑에 정산을 쌓는다
        paymentService.settleOrder(delivery.getOrderId(), dreamiId);
        // 비대면 배달 인증 행 저장 (submittedDtm은 markDelivered가 기록한 deliveryEndDtm 재사용)
        deliveryCertificationRepository.save(
                DeliveryCertification.create(photoKey, delivery.getDeliveryEndDtm(), delivery.getDeliveryId()));
        alarmBoormiDeliveredBySSE(delivery); // 부르미에게_배달완료라고_전달_SSE로()
        return "드리미에게_완료";
    }

    // ===== SSE 알림 =====
    // 모두 트랜잭션 안에서 호출되지만, 실제 전송은 하지 않고 DeliveryNotificationEvent만 발행한다.
    // 트랜잭션이 롤백되면 이 이벤트는 버려지고, 커밋된 경우에만 sendAfterCommit이 실제로 SSE를 보낸다.

    private void alarmBoormiDeliveryStartedBySSE(Delivery delivery) {
        publish(delivery.getBoormiId(), DeliveryEventType.DELIVERY_STARTED_BOORMI,
                DeliveryStatusResponseDto.from(delivery, "배달이 시작되었습니다"));
    }

    private void alarmDreamiDeliveryStartedBySSE(Delivery delivery) {
        publish(delivery.getDreamiId(), DeliveryEventType.DELIVERY_STARTED_DREAMI,
                DeliveryStatusResponseDto.from(delivery, "배달이 시작되었습니다"));
    }

    private void alarmDreamiBoormiCancelBySSE(Delivery delivery) {
        publish(delivery.getDreamiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(delivery, "고객이 주문을 취소했습니다"));
    }

    private void alarmDreamiAdminCancelBySSE(Delivery delivery) {
        publish(delivery.getDreamiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(delivery, "관리자가 배달을 취소했습니다"));
    }

    private void alarmBoormiLocationBySSE(Delivery delivery) {
        publish(delivery.getBoormiId(), DeliveryEventType.DELIVERY_LOCATION,
                DeliveryStatusResponseDto.from(delivery, "위치 갱신됨"));
    }

    private void alarmBoormiDeliveringBySSE(Delivery delivery) {
        publish(delivery.getBoormiId(), DeliveryEventType.DELIVERY_DELIVERING,
                DeliveryStatusResponseDto.from(delivery, "배달이 시작되었습니다"));
    }

    private void alarmBoormiDreamiCancelBySSE(Delivery delivery) {
        publish(delivery.getBoormiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(delivery, "드리미가 픽업을 취소했습니다"));
    }

    private void alarmBoormiAdminCancelBySSE(Delivery delivery) {
        publish(delivery.getBoormiId(), DeliveryEventType.DELIVERY_CANCELLED,
                DeliveryStatusResponseDto.from(delivery, "관리자가 배달을 취소했습니다"));
    }

    private void alarmBoormiDeliveredBySSE(Delivery delivery) {
        publish(delivery.getBoormiId(), DeliveryEventType.DELIVERY_COMPLETED,
                DeliveryStatusResponseDto.from(delivery, "배달이 완료되었습니다"));
    }

    private void publish(UUID userId, DeliveryEventType eventType, DeliveryStatusResponseDto payload) {
        eventPublisher.publishEvent(new DeliveryNotificationEvent(userId, eventType, payload));
    }

    // 트랜잭션 커밋 후에만 실제 알림을 보낸다(커밋 전 발행된 이벤트는 롤백 시 자동으로 버려진다).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(DeliveryNotificationEvent event) {
        notificationService.notify(event.userId(), event.eventType(), event.payload());
    }

}
