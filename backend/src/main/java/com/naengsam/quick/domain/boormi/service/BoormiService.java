package com.naengsam.quick.domain.boormi.service;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import com.naengsam.quick.domain.address.service.DirectionsService;
import com.naengsam.quick.domain.boormi.dto.BoormiDashboardDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueRequest;
import com.naengsam.quick.domain.boormi.dto.OrderRequest;
import com.naengsam.quick.domain.boormi.entity.Charge;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.delivery.dto.RoutePointDto;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.entity.Matching;
import com.naengsam.quick.domain.matching.event.BoormiConfirmedEvent;
import com.naengsam.quick.domain.matching.event.BoormiRejectedDreamiEvent;
import com.naengsam.quick.domain.matching.event.MatchingStartRequestedEvent;
import com.naengsam.quick.domain.matching.event.OrderCancelledByBoormiEvent;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.repository.MatchingRepository;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.dto.BoormiOrdersResponse;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.entity.Role;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.service.PaymentService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoormiService {

    private static final int BASE_SECTION = 1500;   // 기본 구간(m)
    private static final int UNIT_DISTANCE = 100;   // 과금 단위(m)
    private static final int BASE_RATE = 100;       // 기본 구간 100m당 요금(원)
    private static final int BASE_FEE = 3000;       // 기본요금 3000원
    private static final int OVER_RATE = 160;       // 초과 구간 100m당 요금(원)
    private static final int MAX_ACTIVE_ORDERS = 5; // 동시 진행 가능한 요청 수(정책값)
    private static final int TOO_CLOSE_DISTANCE = 50;   // 출발지-도착지 최소 직선거리(m)
    private static final long MARKET_UNIT_PRICE = 5800; // 시장 퀵서비스 건당 평균 단가(절감액 비교 기준)

    private final CoordinatesService coordinatesService;
    private final DirectionsService directionsService;
    private final PaymentService paymentService;
    private final MatchingService matchingService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final MatchingRepository matchingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final GeoDistanceCalculator geoDistanceCalculator;

    /**
     * 부르미의 주문 요청을 접수한다. 출발지/도착지 도로명주소를 좌표로 변환해 주문(ORDERS)을 생성·저장한 뒤 결제를 시작하고 매칭 큐에 등록한다.
     */
    @Transactional
    public UUID subscribeOrder(OrderRequest orderRequest, UUID boormiId) {
        if (orderService.countActiveOrders(boormiId) >= MAX_ACTIVE_ORDERS) {
            throw new BusinessException(OrderErrorCode.TOO_MANY_ACTIVE_ORDERS);
        }

        GeoPoint originCoordinate = toGeoPoint(orderRequest.originAddressLine1());
        GeoPoint destinationCoordinate = toGeoPoint(orderRequest.destinationAddressLine1());

        requireDifferentLocation(originCoordinate, destinationCoordinate);

        // 요금·예상시간은 클라이언트 전송값을 신뢰하지 않고 견적과 동일한 로직으로 서버가 재계산한다.
        // 같은 카카오 응답에서 추천 이동경로 좌표도 함께 받아 주문에 저장한다(추적 지도 폴리라인용).
        KakaoDirectionsResponseDto.Route route = directionsService.getRoute(originCoordinate, destinationCoordinate);
        Charge charge = calculatePrice(route, orderRequest.itemCd());
        String routePath = toRoutePathJson(route);

        UUID orderId = UUID.randomUUID();

        Addresses addresses = Addresses.builder()
                .originAddressLine1(orderRequest.originAddressLine1())
                .originAddressLine2(orderRequest.originAddressLine2())
                .originLatitude(originCoordinate.latitude())
                .originLongitude(originCoordinate.longitude())
                .destinationAddressLine1(orderRequest.destinationAddressLine1())
                .destinationAddressLine2(orderRequest.destinationAddressLine2())
                .destinationLatitude(destinationCoordinate.latitude())
                .destinationLongitude(destinationCoordinate.longitude())
                .build();

        Orders orders = Orders.create(orderId, boormiId, orderRequest.itemName(),
                orderRequest.itemCd(), orderRequest.itemDetail(),
                (long) charge.amount(), charge.eta(), (long) charge.distance(),
                orderRequest.deliveryRequest(), orderRequest.imageKey(), addresses, routePath);

        orderService.createOrders(orders);
        paymentService.payWithPoint(boormiId, orderId, charge.amount());
        if (matchingService.isActiveGroupExists(orderId)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
        // 엔진은 매칭 시작 즉시 드리미에게 오퍼 팝업을 보내므로, 주문이 커밋된 뒤에 제출해야 드리미가 그 주문을 조회할 수 있다.
        eventPublisher.publishEvent(new MatchingStartRequestedEvent(orders));
        return orders.getOrderId();
    }

    /**
     * 부르미가 접수한 주문을 취소한다. 매칭 성사 전(MATCHING, PENDING_BOORMI_CONFIRMATION) 상태에서만 취소할 수 있으며, 주문 상태를 CANCELLED 로 바꾸고 매칭 큐에서도
     * 제안을 회수한다.
     */
    @Transactional
    public void unsubscribeOrder(UUID boormiId, UUID orderId) {
        Orders order = orderService.getOrder(orderId);

        if (!order.getBoormiId().equals(boormiId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_OWNER);
        }

        if (!(order.getOrderCd().equals(OrderCd.MATCHING)
                || order.getOrderCd().equals(OrderCd.PENDING_BOORMI_CONFIRMATION)
        )) {
            throw new BusinessException(OrderErrorCode.CANNOT_CANCEL_AFTER_PICKUP);
        }

        orderService.cancel(order, CancelerCd.BOORMI); // 주문 취소 상태 전이 + 취소 이력 저장
        paymentService.refundByPoint(orderId);         // 결제 포인트 전액 환불 (SSE 알림 전에 DB 작업을 끝낸다)

        // 커밋 전에 제출하면 취소가 롤백돼도 인메모리 방은 이미 종료된 채로 남아 주문이 영영 재매칭되지 않는다.
        // 제안 회수 + 드리미 SSE 알림은 커밋 후 MatchingService 의 리스너가 담당한다.
        eventPublisher.publishEvent(new OrderCancelledByBoormiEvent(orderId));
    }

    /**
     * 부르미가 수락한 드리미를 최종 확정한다. 확정 대기(PENDING_BOORMI_CONFIRMATION) 상태의 자기 주문만 확정할 수 있으며, DB 주문을 IN_PROGRESS 로 전이한 뒤 매칭엔진에
     * 부르미 수락을 제출한다. 매칭엔진 제출은 이 트랜잭션이 커밋된 뒤에 일어난다.
     */
    @Transactional
    public void confirmDreami(UUID boormiId, UUID orderId, UUID offerId) {
        Orders order = orderService.getOrder(orderId);

        if (!order.getBoormiId().equals(boormiId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_OWNER);
        }
        if (!order.getOrderCd().equals(OrderCd.PENDING_BOORMI_CONFIRMATION)) {
            throw new BusinessException(OrderErrorCode.INVALID_DREAMI_CONFIRMATION);
        }

        UUID dreamiId = matchingService.findDreamiIdByOfferId(offerId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.NO_DREAMI_TO_CONFIRM));

        order.confirmDreami(dreamiId);           // IN_PROGRESS 전이 + dreami_id 반영 (dirty checking)

        Matching matching = Matching.create(orderId);
        matching.markAccepted();                 // 확정 순간을 매칭 성사 시각으로 기록
        matchingRepository.save(matching);       // MATCHING 이력 저장

        // 매칭엔진은 별도 스레드/트랜잭션에서 배달을 시작하므로, 이 트랜잭션이 커밋된 뒤에 제출해야
        // IN_PROGRESS 전이를 볼 수 있다. 커밋 후 처리는 MatchingService 의 리스너가 담당한다.
        eventPublisher.publishEvent(new BoormiConfirmedEvent(offerId));
    }
    
    /**
     * 부르미가 확정 대기 중인 드리미를 거절한다. 확정 대기(PENDING_BOORMI_CONFIRMATION) 상태의 자기 주문만 거절할 수 있으며, DB 주문을 다시 MATCHING 으로 되돌린 뒤
     * 매칭엔진에 부르미 거절을 제출한다. 거절당한 드리미 알림과 재오퍼는 매칭엔진이 담당한다.
     */
    @Transactional
    public void rejectDreami(UUID boormiId, UUID orderId, UUID offerId) {
        Orders order = orderService.getOrder(orderId);

        if (!order.getBoormiId().equals(boormiId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_OWNER);
        }
        if (!order.getOrderCd().equals(OrderCd.PENDING_BOORMI_CONFIRMATION)) {
            throw new BusinessException(OrderErrorCode.CANNOT_CANCEL);
        }
        if (!matchingService.isBoormiOfferOwner(offerId, boormiId)) {
            throw new BusinessException(MatchingErrorCode.NOT_OFFER_OWNER);
        }

        order.rejectDreami();                    // MATCHING 복귀 + dreami_id 해제 (dirty checking)

        // 엔진은 거절 즉시 재오퍼를 돌리므로, 커밋 전에 제출하면 다른 드리미가 먼저 수락해 커밋한
        // PENDING_BOORMI_CONFIRMATION 을 이 트랜잭션의 MATCHING 복귀가 덮어써 주문이 고착된다.
        eventPublisher.publishEvent(new BoormiRejectedDreamiEvent(offerId));
    }

    /**
     * 부르미가 신청한 주문 목록을 최신순 커서 페이지네이션으로 조회한다. status 로 단일 상태 필터링이 가능하다.
     */
    @Transactional(readOnly = true)
    public BoormiOrdersResponse getMyOrders(UUID boormiId, String cursor, int size, OrderCd status) {
        return orderService.getOrders(boormiId, Role.BOORMI, cursor, size, status);
    }

    /**
     * 출발지/도착지 도로명주소를 좌표로 변환한 뒤 카카오 길찾기로 실제 거리·소요시간을 구하고, 물건 유형 배율을 반영한 예상 가격/시간/거리를 반환한다.
     */
    @Transactional(readOnly = true)
    public ExpectedValueDto expectedValue(ExpectedValueRequest request) {
        GeoPoint origin = toGeoPoint(request.originAddressLine1());
        GeoPoint destination = toGeoPoint(request.destinationAddressLine1());

        requireDifferentLocation(origin, destination);

        KakaoDirectionsResponseDto.Route route = directionsService.getRoute(origin, destination);
        Charge charge = calculatePrice(route, request.itemCd());

        return new ExpectedValueDto(charge.amount(), charge.eta(), charge.distance());
    }

    /**
     * 부르미 대시보드 — 누적 완료 건수, 시장 퀵서비스 대비 절감 금액, 이번 달 이용 건수를 조회한다. 절감액은 완료 건수를 시장 평균 단가로 환산한 값에서 실제 결제액을 뺀 값이며, 실제 결제액이 더 크면
     * 0으로 둔다. 주문에는 완료 시각이 없으므로 이번 달 건수는 배달 완료 시각(DELIVERY.delivery_end_dtm) 기준으로 센다.
     */
    @Transactional(readOnly = true)
    public BoormiDashboardDto getDashboard(UUID boormiId) {
        long completedCount = orderRepository.countByBoormiIdAndOrderCd(boormiId, OrderCd.COMPLETED);
        long paidAmount = orderRepository.sumCompletedDeliveryAmount(boormiId);
        long totalSavedAmount = Math.max(0, completedCount * MARKET_UNIT_PRICE - paidAmount);

        YearMonth thisMonth = YearMonth.now();
        long thisMonthCount = deliveryRepository.countDeliveredByBoormiBetween(boormiId,
                thisMonth.atDay(1).atStartOfDay(), thisMonth.plusMonths(1).atDay(1).atStartOfDay());

        return BoormiDashboardDto.of(completedCount, totalSavedAmount, thisMonthCount);
    }

    /**
     * 두 좌표의 실제 도보 거리·소요시간을 카카오 길찾기로 조회한 뒤 요금과 예상시간(분)을 계산한다. 견적 조회와 주문 접수가 같은 요금을 산출하도록 공유한다. 기본 1.5km까지는 100m당 100원,
     * 초과 구간은 100m당 160원으로 과금하고 물건 유형 배율을 곱한다.
     */
    private Charge calculatePrice(KakaoDirectionsResponseDto.Route route, ItemCd itemCd) {
        KakaoDirectionsResponseDto.Properties properties = route.properties();

        //비용 계산
        int baseDistance = Math.min(properties.totalDistance(), BASE_SECTION);
        int overDistance = Math.max(properties.totalDistance() - BASE_SECTION, 0);
        int price = (baseDistance / UNIT_DISTANCE) * BASE_RATE
                + (overDistance / UNIT_DISTANCE) * OVER_RATE
                + BASE_FEE;

        //예상 시간
        int eta = (int) Math.ceil(properties.totalTime() / 60.0);
        return new Charge(properties.totalDistance(), (int) Math.round(price * ItemCd.multiplier(itemCd)), eta);
    }

    /**
     * 카카오 경로의 실제 이동경로 좌표를 {@code [{latitude, longitude}, ...]} JSON 문자열로 직렬화한다. 주문에 저장해 두면 추적 지도가 폴리라인으로 그린다.
     */
    private String toRoutePathJson(KakaoDirectionsResponseDto.Route route) {
        try {
            return objectMapper.writeValueAsString(RoutePointDto.from(route));
        } catch (JacksonException e) {
            log.error("카카오 경로 좌표 JSON 직렬화 실패 — 경로 없이 주문을 진행한다", e);
            return null;
        }
    }

    /**
     * 출발지와 도착지가 사실상 같은 위치인지 검증한다. 두 좌표의 직선거리가 {@link #TOO_CLOSE_DISTANCE}m 미만이면 SAME_ORIGIN_DESTINATION 예외를 던진다. 견적
     * 조회·주문 접수 모두 카카오 도보 API 호출 전에 이 가드를 통과해야 한다 — 같은 좌표면 카카오가 경로를 반환하지 못해 EXTERNAL_SERVICE_ERROR 로 실패하기 때문이다.
     */
    private void requireDifferentLocation(GeoPoint origin, GeoPoint destination) {
        if (geoDistanceCalculator.distanceMeters(origin, destination) < TOO_CLOSE_DISTANCE) {
            throw new BusinessException(OrderErrorCode.SAME_ORIGIN_DESTINATION);
        }
    }

    private GeoPoint toGeoPoint(String roadAddress) {
        CoordinatesResponseDto coordinates = coordinatesService.getCoordinates(roadAddress);
        List<CoordinatesResponseDto.Document> documents = coordinates.documents();
        if (documents.isEmpty()) {
            throw new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        CoordinatesResponseDto.RoadAddress address = documents.getFirst().roadAddress();

        // x=경도, y=위도 → GeoPoint(latitude, longitude) 순서에 맞춰 매핑
        return new GeoPoint(new BigDecimal(address.y()), new BigDecimal(address.x()));
    }
}
