package com.naengsam.quick.domain.boormi.service;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import com.naengsam.quick.domain.address.service.KakaoDirectionsService;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueRequest;
import com.naengsam.quick.domain.boormi.dto.OrderRequest;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.service.PaymentService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final int EARTH_RADIUS = 6_371_000;  // 지구 반지름(m)
    
    private final CoordinatesService coordinatesService;
    private final KakaoDirectionsService kakaoDirectionsService;
    private final PaymentService paymentService;
    private final MatchingService matchingService;
    private final OrderService orderService;

    /**
     * 부르미의 주문 요청을 접수한다. 출발지/도착지 도로명주소를 좌표로 변환해 주문(ORDERS)을 생성·저장한 뒤 결제를 시작하고 매칭 큐에 등록한다.
     */
    @Transactional
    public void subscribeOrder(OrderRequest orderRequest, UUID boormiId) {
        if (orderService.countActiveOrders(boormiId) >= MAX_ACTIVE_ORDERS) {
            throw new BusinessException(OrderErrorCode.TOO_MANY_ACTIVE_ORDERS);
        }

        GeoPoint originCoordinate = toGeoPoint(orderRequest.originAddressLine1());
        GeoPoint destinationCoordinate = toGeoPoint(orderRequest.destinationAddressLine1());
        
        if (isTooClose(originCoordinate, destinationCoordinate)) {
            throw new BusinessException(OrderErrorCode.SAME_ORIGIN_DESTINATION);
        }

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
                (long) orderRequest.deliveryAmount(), orderRequest.deliveryEta(),
                orderRequest.deliveryRequest(), orderRequest.imageUrl(), addresses);

        orderService.createOrders(orders);
        paymentService.startPayment();
        if (!matchingService.startMatching(orders)) {
            throw new BusinessException(GeneralErrorCode.CONFLICT);
        }
    }

    /**
     * 부르미가 접수한 주문을 취소한다. 매칭 성사 전(MATCHING, PENDING_BOORMI_CONFIRMATION) 상태에서만 취소할 수 있으며, 주문 상태를 CANCELLED 로 바꾸고 매칭 큐에서도 제안을 회수한다.
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
        matchingService.cancelOrderByBoormi(orderId);  // 제안 회수 + 드리미 SSE 알림
        // TODO: 결제 취소/환불 연동 (PaymentService 구현 후)
    }

    /**
     * 출발지/도착지 도로명주소를 좌표로 변환한 뒤 카카오 길찾기로 실제 거리·소요시간을 구하고, 물건 유형 배율을 반영한 예상 가격/시간/거리를 반환한다.
     */
    @Transactional(readOnly = true)
    public ExpectedValueDto expectedValue(ExpectedValueRequest request) {
        GeoPoint origin = toGeoPoint(request.originAddressLine1());
        GeoPoint destination = toGeoPoint(request.destinationAddressLine1());

        KakaoDirectionsResponseDto.Properties route = kakaoDirectionsService.getRoute(origin, destination);

        int expectedValue = calPrice(route.totalDistance(), request.itemCd());
        int expectedTime = (int) Math.ceil(route.totalTime() / 60.0);

        return new ExpectedValueDto(expectedValue, expectedTime, route.totalDistance());
    }

    /**
     * 출발지와 도착지가 너무 가까운지 판단한다. 두 좌표의 직선거리가 {@link #TOO_CLOSE_DISTANCE}m 미만이면 사실상 같은 위치로 본다.
     */
    private boolean isTooClose(GeoPoint origin, GeoPoint destination) {
        return distanceMeters(origin, destination) < TOO_CLOSE_DISTANCE;
    }

    /**
     * 두 좌표 사이의 하버사인 직선거리(m)를 계산한다.
     */
    private double distanceMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude().doubleValue());
        double lat2 = Math.toRadians(b.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude().doubleValue() - a.longitude().doubleValue());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.sqrt(h));
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

    /**
     * 거리(m)에 따라 요금을 계산한다. 기본 1.5km까지는 100m당 100원, 초과 구간은 100m당 160원으로 과금하고 물건 유형 배율을 곱한다.
     */
    private int calPrice(int distance, ItemCd itemCd) {
        int baseDistance = Math.min(distance, BASE_SECTION);
        int overDistance = Math.max(distance - BASE_SECTION, 0);

        int price = (baseDistance / UNIT_DISTANCE) * BASE_RATE
                + (overDistance / UNIT_DISTANCE) * OVER_RATE
                + BASE_FEE;

        return (int) Math.round(price * ItemCd.multiplier(itemCd));
    }
}

