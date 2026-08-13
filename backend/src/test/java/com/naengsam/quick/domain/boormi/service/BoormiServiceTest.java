package com.naengsam.quick.domain.boormi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.naengsam.quick.domain.address.dto.CoordinatesResponseDto;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.service.CoordinatesService;
import com.naengsam.quick.domain.address.service.DirectionsService;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueDto;
import com.naengsam.quick.domain.boormi.dto.ExpectedValueRequest;
import com.naengsam.quick.domain.boormi.dto.OrderRequest;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.boormi.dto.BoormiDashboardDto;
import com.naengsam.quick.domain.boormi.dto.MonthlySavingDto;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.delivery.dto.MonthlySavingAggregate;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.event.BoormiConfirmedEvent;
import com.naengsam.quick.domain.matching.event.BoormiRejectedDreamiEvent;
import com.naengsam.quick.domain.matching.event.MatchingStartRequestedEvent;
import com.naengsam.quick.domain.matching.event.OrderCancelledByBoormiEvent;
import com.naengsam.quick.domain.matching.exception.MatchingErrorCode;
import com.naengsam.quick.domain.matching.repository.MatchingRepository;
import com.naengsam.quick.domain.matching.service.GeoDistanceCalculator;
import com.naengsam.quick.domain.matching.service.MatchingService;
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.service.PaymentService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 예상 견적(가격/시간/거리) 계산 로직 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class
BoormiServiceTest {

    @Mock
    private BoormiRepository boormiRepository;

    @Mock
    private CoordinatesService coordinatesService;

    @Mock
    private DirectionsService directionsService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private OrderService orderService;

    @Mock
    private MatchingRepository matchingRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // 실제 직렬화 결과(route_path JSON)를 검증하기 위해 진짜 ObjectMapper 를 주입한다.
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // 임계값(50m) 판정에 실제 하버사인 계산이 필요하므로 진짜 GeoDistanceCalculator 를 주입한다.
    @Spy
    private GeoDistanceCalculator geoDistanceCalculator = new GeoDistanceCalculator();

    @InjectMocks
    private BoormiService boormiService;

    // 카카오 Route 목 픽스처. 총 거리·시간(요금 계산용)과 실제 이동경로 좌표(path.points, [경도, 위도])를 담는다.
    private static KakaoDirectionsResponseDto.Route routeOf(int totalDistance, int totalTime) {
        return routeOf(totalDistance, totalTime, new double[0][]);
    }

    private static KakaoDirectionsResponseDto.Route routeOf(int totalDistance, int totalTime, double[][] points) {
        KakaoDirectionsResponseDto.Path path = new KakaoDirectionsResponseDto.Path(points);
        KakaoDirectionsResponseDto.StepProperties stepProps =
                new KakaoDirectionsResponseDto.StepProperties(totalDistance, "안내", totalTime, 0, 0);
        KakaoDirectionsResponseDto.Step step = new KakaoDirectionsResponseDto.Step(stepProps, path);
        KakaoDirectionsResponseDto.Leg leg = new KakaoDirectionsResponseDto.Leg(
                new KakaoDirectionsResponseDto.LegProperties(totalDistance, totalTime),
                new KakaoDirectionsResponseDto.Step[]{step});
        return new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(totalDistance, totalTime),
                new KakaoDirectionsResponseDto.Leg[]{leg});
    }

    private static ExpectedValueRequest request(ItemCd itemCd) {
        return new ExpectedValueRequest("서울시 강남구", "서울시 서초구", itemCd);
    }

    private static OrderRequest orderRequest() {
        return new OrderRequest("서울시 강남구", "101동", "서울시 서초구", "202동",
                "서류봉투", ItemCd.DOCUMENT, "http://img", "계약서", "문 앞에 두세요");
    }

    private static OrderRequest sameLocationOrderRequest() {
        return new OrderRequest("서울시 강남구", "101동", "서울시 강남구", "101동",
                "서류봉투", ItemCd.DOCUMENT, "http://img", "계약서", "문 앞에 두세요");
    }

    // 주소 문자열은 다르지만 좌표는 임계값(50m) 이내로 아주 가까운 요청
    private static OrderRequest nearbyOrderRequest() {
        return new OrderRequest("서울시 강남구 A", "101동", "서울시 강남구 B", "202동",
                "서류봉투", ItemCd.DOCUMENT, "http://img", "계약서", "문 앞에 두세요");
    }

    // x=경도(longitude), y=위도(latitude)
    private static CoordinatesResponseDto coordinatesAt(String longitudeX, String latitudeY) {
        CoordinatesResponseDto.RoadAddress roadAddress =
                new CoordinatesResponseDto.RoadAddress(
                        null, null, null, null, null, null, null, null, null,
                        longitudeX, latitudeY);
        return new CoordinatesResponseDto(List.of(new CoordinatesResponseDto.Document(roadAddress)));
    }

    private static Orders order(UUID boormiId, OrderCd orderCd) {
        GeoPoint point = new GeoPoint(new BigDecimal("37.0"), new BigDecimal("127.0"));
        Orders order = Orders.create(UUID.randomUUID(), boormiId, point, point);
        ReflectionTestUtils.setField(order, "orderCd", orderCd);
        return order;
    }

    private static Orders confirmableOrder(UUID boormiId, UUID dreamiId, OrderCd orderCd) {
        Orders order = order(boormiId, orderCd);
        ReflectionTestUtils.setField(order, "dreamiId", dreamiId);
        return order;
    }

    @Test
    void 문서_5km면_기본요금과_거리요금을_합산한다() {
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        given(directionsService.getRoute(any(), any()))
                .willReturn(routeOf(5000, 900));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.DOCUMENT));

        assertThat(result.expectedValue()).isEqualTo(10100);
        assertThat(result.expectedTime()).isEqualTo(15);
        assertThat(result.expectedDistance()).isEqualTo(5000);
    }

    @Test
    void PACKAGE는_배율15이_곱해진다() {
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        given(directionsService.getRoute(any(), any()))
                .willReturn(routeOf(5000, 900));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.PACKAGE));

        assertThat(result.expectedValue()).isEqualTo(15150);
    }

    @Test
    void ETA는_초를_분으로_올림한다() {
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        given(directionsService.getRoute(any(), any()))
                .willReturn(routeOf(5000, 901));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.DOCUMENT));

        assertThat(result.expectedTime()).isEqualTo(16);
    }

    @Test
    void 견적_좌표변환시_x는_경도_y는_위도로_매핑한다() {
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        given(directionsService.getRoute(any(), any()))
                .willReturn(routeOf(5000, 900));

        boormiService.expectedValue(request(ItemCd.DOCUMENT));

        ArgumentCaptor<GeoPoint> captor = ArgumentCaptor.forClass(GeoPoint.class);
        then(directionsService).should().getRoute(captor.capture(), captor.capture());
        GeoPoint origin = captor.getAllValues().getFirst();
        assertThat(origin.latitude()).isEqualByComparingTo("37.5");   // y=위도
        assertThat(origin.longitude()).isEqualByComparingTo("127.0"); // x=경도
    }

    @Test
    void 주문접수_요청필드로_주문을_생성해_저장하고_결제하며_커밋후_처리용_매칭시작_이벤트를_발행한다() {
        UUID boormiId = UUID.randomUUID();
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        given(directionsService.getRoute(any(), any()))
                .willReturn(routeOf(5000, 900));

        boormiService.subscribeOrder(orderRequest(), boormiId);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        then(orderService).should().createOrders(captor.capture());
        // 결제는 저장된 주문과 같은 orderId, 서버가 재계산한 요금으로 이뤄져야 한다
        then(paymentService).should()
                .payWithPoint(boormiId, captor.getValue().getOrderId(), 10100L);
        then(matchingService).should(never()).startMatching(any()); // 커밋 전에는 엔진에 직접 제출하지 않는다
        then(eventPublisher).should().publishEvent(new MatchingStartRequestedEvent(captor.getValue()));

        Orders saved = captor.getValue();
        assertThat(saved.getBoormiId()).isEqualTo(boormiId);
        assertThat(saved.getItemName()).isEqualTo("서류봉투");
        assertThat(saved.getItemCd()).isEqualTo(ItemCd.DOCUMENT);
        // 요금·예상시간은 클라이언트값이 아니라 서버가 좌표·거리로 재계산한 값(5000m DOCUMENT → 10100원, 900초 → 15분)
        assertThat(saved.getDeliveryAmount()).isEqualTo(10100L);
        assertThat(saved.getDeliveryEta()).isEqualTo(15);
        assertThat(saved.getOrderCd()).isEqualTo(OrderCd.MATCHING);
        assertThat(saved.getOriginAddressLine1()).isEqualTo("서울시 강남구");
        assertThat(saved.getDestinationAddressLine2()).isEqualTo("202동");
        // x=경도(127.x), y=위도(37.x)가 latitude/longitude 자리에 올바르게 매핑되어야 한다
        assertThat(saved.getOriginLatitude()).isEqualByComparingTo("37.5");
        assertThat(saved.getOriginLongitude()).isEqualByComparingTo("127.0");
        assertThat(saved.getDestinationLatitude()).isEqualByComparingTo("37.6");
        assertThat(saved.getDestinationLongitude()).isEqualByComparingTo("127.1");
    }

    @Test
    void 주문접수_배달요금은_클라이언트값이_아니라_서버가_재계산한_값으로_저장된다() {
        UUID boormiId = UUID.randomUUID();
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        // 거리 2000m, PACKAGE(배율 1.5) → (1500/100*100 + 500/100*160 + 3000)=5300 → ×1.5 = 7950원, 660초 → 11분
        given(directionsService.getRoute(any(), any()))
                .willReturn(routeOf(2000, 660));

        OrderRequest packageOrder = new OrderRequest("서울시 강남구", "101동", "서울시 서초구", "202동",
                "노트북", ItemCd.PACKAGE, "http://img", "파손주의", "문 앞에 두세요");
        boormiService.subscribeOrder(packageOrder, boormiId);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        then(orderService).should().createOrders(captor.capture());
        Orders saved = captor.getValue();
        assertThat(saved.getDeliveryAmount()).isEqualTo(7950L);
        assertThat(saved.getDeliveryEta()).isEqualTo(11);
    }

    @Test
    void 주문접수_카카오_경로좌표를_route_path_JSON으로_저장한다() {
        UUID boormiId = UUID.randomUUID();
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        // path.points 는 [경도, 위도] 순서 → 저장 시 (위도, 경도)로 뒤집혀 직렬화돼야 한다.
        double[][] points = {{127.02700693, 37.49864277}, {127.02698289, 37.49863151}};
        given(directionsService.getRoute(any(), any())).willReturn(routeOf(5000, 900, points));

        boormiService.subscribeOrder(orderRequest(), boormiId);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        then(orderService).should().createOrders(captor.capture());
        String routePath = captor.getValue().getRoutePath();
        assertThat(routePath)
                .contains("\"latitude\":37.49864277")
                .contains("\"longitude\":127.02700693")
                .contains("\"latitude\":37.49863151")
                .contains("\"longitude\":127.02698289");
    }

    @Test
    void 주문접수_route_path_직렬화가_실패해도_경로없이_저장_결제_매칭을_진행한다() {
        UUID boormiId = UUID.randomUUID();
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        given(directionsService.getRoute(any(), any())).willReturn(routeOf(5000, 900));
        willThrow(new JacksonException("직렬화 실패") {
        }).given(objectMapper).writeValueAsString(any());

        boormiService.subscribeOrder(orderRequest(), boormiId);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        then(orderService).should().createOrders(captor.capture());
        Orders saved = captor.getValue();
        assertThat(saved.getRoutePath()).isNull();
        then(paymentService).should().payWithPoint(boormiId, saved.getOrderId(), 10100L);
        then(eventPublisher).should().publishEvent(new MatchingStartRequestedEvent(saved));
    }

    @Test
    void 주문접수_출발지와_도착지가_같으면_SAME_ORIGIN_DESTINATION() {
        UUID boormiId = UUID.randomUUID();
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));

        Throwable thrown = catchThrowable(
                () -> boormiService.subscribeOrder(sameLocationOrderRequest(), boormiId));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.SAME_ORIGIN_DESTINATION);
        then(orderService).should(never()).createOrders(any());
    }

    @Test
    void 주문접수_주소는_달라도_좌표가_임계값이내면_SAME_ORIGIN_DESTINATION() {
        UUID boormiId = UUID.randomUUID();
        // 위도 0.00036도 차 ≈ 약 40m < 50m 임계값 (주소 문자열은 서로 다름)
        given(coordinatesService.getCoordinates("서울시 강남구 A")).willReturn(coordinatesAt("127.0", "37.50000"));
        given(coordinatesService.getCoordinates("서울시 강남구 B")).willReturn(coordinatesAt("127.0", "37.50036"));

        Throwable thrown = catchThrowable(
                () -> boormiService.subscribeOrder(nearbyOrderRequest(), boormiId));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.SAME_ORIGIN_DESTINATION);
        then(orderService).should(never()).createOrders(any());
    }

    @Test
    void 주문접수_진행중_요청이_한도이상이면_TOO_MANY_ACTIVE_ORDERS() {
        UUID boormiId = UUID.randomUUID();
        given(orderService.countActiveOrders(any())).willReturn(5L);

        Throwable thrown = catchThrowable(() -> boormiService.subscribeOrder(orderRequest(), boormiId));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.TOO_MANY_ACTIVE_ORDERS);
        then(orderService).should(never()).createOrders(any());
    }

    @Test
    void 주문접수_매칭시작이_실패하면_CONFLICT() {
        UUID boormiId = UUID.randomUUID();
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));
        given(coordinatesService.getCoordinates("서울시 서초구")).willReturn(coordinatesAt("127.1", "37.6"));
        given(directionsService.getRoute(any(), any()))
                .willReturn(routeOf(5000, 900));
        given(matchingService.isActiveGroupExists(any())).willReturn(true);

        Throwable thrown = catchThrowable(() -> boormiService.subscribeOrder(orderRequest(), boormiId));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.CONFLICT);
        then(eventPublisher).should(never()).publishEvent(any(MatchingStartRequestedEvent.class));
    }

    @Test
    void 견적_출발지와_도착지가_같으면_카카오호출없이_SAME_ORIGIN_DESTINATION() {
        given(coordinatesService.getCoordinates("서울시 강남구")).willReturn(coordinatesAt("127.0", "37.5"));

        ExpectedValueRequest sameLocation =
                new ExpectedValueRequest("서울시 강남구", "서울시 강남구", ItemCd.DOCUMENT);
        Throwable thrown = catchThrowable(() -> boormiService.expectedValue(sameLocation));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.SAME_ORIGIN_DESTINATION);
        then(directionsService).should(never()).getRoute(any(), any());
    }

    @Test
    void 좌표변환_결과가_비면_EXTERNAL_SERVICE_ERROR() {
        given(coordinatesService.getCoordinates(anyString()))
                .willReturn(new CoordinatesResponseDto(List.of()));

        Throwable thrown = catchThrowable(() -> boormiService.expectedValue(request(ItemCd.DOCUMENT)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(GeneralErrorCode.EXTERNAL_SERVICE_ERROR);
    }

    @Test
    void 취소_주문이_없으면_ORDER_NOT_FOUND_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        given(orderService.getOrder(orderId))
                .willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        Throwable thrown = catchThrowable(() -> boormiService.unsubscribeOrder(boormiId, orderId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        then(orderService).should(never()).cancel(any(Orders.class), any());
        then(paymentService).should(never()).refundByPoint(any());
        then(eventPublisher).should(never()).publishEvent(any(OrderCancelledByBoormiEvent.class));
    }

    @Test
    void 취소_주문_소유자가_아니면_NOT_ORDER_OWNER_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders order = order(UUID.randomUUID(), OrderCd.MATCHING);
        given(orderService.getOrder(orderId)).willReturn(order);

        Throwable thrown = catchThrowable(() -> boormiService.unsubscribeOrder(boormiId, orderId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.NOT_ORDER_OWNER);
        then(orderService).should(never()).cancel(any(Orders.class), any());
        then(paymentService).should(never()).refundByPoint(any());
        then(eventPublisher).should(never()).publishEvent(any(OrderCancelledByBoormiEvent.class));
    }

    @Test
    void 취소_이미_진행중이면_CANNOT_CANCEL_AFTER_PICKUP_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders order = order(boormiId, OrderCd.IN_PROGRESS);
        given(orderService.getOrder(orderId)).willReturn(order);

        Throwable thrown = catchThrowable(() -> boormiService.unsubscribeOrder(boormiId, orderId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.CANNOT_CANCEL_AFTER_PICKUP);
        then(orderService).should(never()).cancel(any(Orders.class), any());
        then(paymentService).should(never()).refundByPoint(any());
        then(eventPublisher).should(never()).publishEvent(any(OrderCancelledByBoormiEvent.class));
    }

    @Test
    void 취소_정상이면_주문취소와_포인트환불후_커밋후_처리용_이벤트를_발행한다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders order = order(boormiId, OrderCd.MATCHING);
        given(orderService.getOrder(orderId)).willReturn(order);

        boormiService.unsubscribeOrder(boormiId, orderId);

        then(orderService).should().cancel(order, CancelerCd.BOORMI);
        then(paymentService).should().refundByPoint(orderId);
        then(matchingService).should(never()).cancelOrderByBoormi(any()); // 커밋 전에는 엔진에 직접 제출하지 않는다
        then(eventPublisher).should().publishEvent(new OrderCancelledByBoormiEvent(orderId));
    }

    @Test
    void 확정_정상이면_dreamiId를_채우고_IN_PROGRESS_전이하며_커밋후_처리용_이벤트를_발행한다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        Orders order = order(boormiId, OrderCd.PENDING_BOORMI_CONFIRMATION); // dreamiId 미설정 상태로 시작
        given(orderService.getOrder(orderId)).willReturn(order);
        given(matchingService.findDreamiIdByOfferId(offerId)).willReturn(Optional.of(dreamiId));

        boormiService.confirmDreami(boormiId, orderId, offerId);

        assertThat(order.getOrderCd()).isEqualTo(OrderCd.IN_PROGRESS);
        assertThat(order.getDreamiId()).isEqualTo(dreamiId);
        then(matchingRepository).should().save(any());
        then(matchingService).should(never()).acceptByBoormi(any()); // 커밋 전에는 엔진에 직접 제출하지 않는다
        then(eventPublisher).should().publishEvent(new BoormiConfirmedEvent(offerId));
    }

    @Test
    void 확정_비소유자면_NOT_ORDER_OWNER_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders order = confirmableOrder(UUID.randomUUID(), UUID.randomUUID(),
                OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderService.getOrder(orderId)).willReturn(order);

        Throwable thrown = catchThrowable(
                () -> boormiService.confirmDreami(boormiId, orderId, UUID.randomUUID()));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.NOT_ORDER_OWNER);
        then(eventPublisher).should(never()).publishEvent(any(BoormiConfirmedEvent.class));
    }

    @Test
    void 확정_상태가_PENDING_BOORMI_CONFIRMATION이_아니면_INVALID_DREAMI_CONFIRMATION_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders order = confirmableOrder(boormiId, UUID.randomUUID(), OrderCd.MATCHING);
        given(orderService.getOrder(orderId)).willReturn(order);

        Throwable thrown = catchThrowable(
                () -> boormiService.confirmDreami(boormiId, orderId, UUID.randomUUID()));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.INVALID_DREAMI_CONFIRMATION);
        then(eventPublisher).should(never()).publishEvent(any(BoormiConfirmedEvent.class));
    }

    @Test
    void 확정_offer에_드리미가_없으면_NO_DREAMI_TO_CONFIRM_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        Orders order = order(boormiId, OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderService.getOrder(orderId)).willReturn(order);
        given(matchingService.findDreamiIdByOfferId(offerId)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(
                () -> boormiService.confirmDreami(boormiId, orderId, offerId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.NO_DREAMI_TO_CONFIRM);
        then(matchingRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(BoormiConfirmedEvent.class));
    }

    @Test
    void 거절_정상이면_MATCHING으로_되돌리고_매칭엔진에_거절을_제출한다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        Orders order = confirmableOrder(boormiId, UUID.randomUUID(),
                OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderService.getOrder(orderId)).willReturn(order);
        given(matchingService.isBoormiOfferOwner(offerId, boormiId)).willReturn(true);

        boormiService.rejectDreami(boormiId, orderId, offerId);

        assertThat(order.getOrderCd()).isEqualTo(OrderCd.MATCHING);
        assertThat(order.getDreamiId()).isNull();
        then(matchingService).should(never()).rejectByBoormi(any()); // 커밋 전에는 엔진에 직접 제출하지 않는다
        then(eventPublisher).should().publishEvent(new BoormiRejectedDreamiEvent(offerId));
        then(matchingRepository).should(never()).save(any());
    }

    @Test
    void 거절_비소유자면_NOT_ORDER_OWNER_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders order = order(UUID.randomUUID(), OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderService.getOrder(orderId)).willReturn(order);

        Throwable thrown = catchThrowable(
                () -> boormiService.rejectDreami(boormiId, orderId, UUID.randomUUID()));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.NOT_ORDER_OWNER);
        then(eventPublisher).should(never()).publishEvent(any(BoormiRejectedDreamiEvent.class));
    }

    @Test
    void 거절_상태가_PENDING_BOORMI_CONFIRMATION이_아니면_CANNOT_CANCEL_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Orders order = order(boormiId, OrderCd.MATCHING);
        given(orderService.getOrder(orderId)).willReturn(order);

        Throwable thrown = catchThrowable(
                () -> boormiService.rejectDreami(boormiId, orderId, UUID.randomUUID()));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(OrderErrorCode.CANNOT_CANCEL);
        then(eventPublisher).should(never()).publishEvent(any(BoormiRejectedDreamiEvent.class));
    }

    @Test
    void 거절_제안이_본인_주문의_것이_아니면_NOT_OFFER_OWNER_예외() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        Orders order = order(boormiId, OrderCd.PENDING_BOORMI_CONFIRMATION);
        given(orderService.getOrder(orderId)).willReturn(order);
        given(matchingService.isBoormiOfferOwner(offerId, boormiId)).willReturn(false);

        Throwable thrown = catchThrowable(() -> boormiService.rejectDreami(boormiId, orderId, offerId));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(MatchingErrorCode.NOT_OFFER_OWNER);
        assertThat(order.getOrderCd()).isEqualTo(OrderCd.PENDING_BOORMI_CONFIRMATION);
        then(eventPublisher).should(never()).publishEvent(any(BoormiRejectedDreamiEvent.class));
    }

    @Test
    void 대시보드는_완료_건수와_절감액과_이번달_건수를_반환한다() {
        UUID boormiId = UUID.randomUUID();
        YearMonth thisMonth = YearMonth.now();
        given(orderRepository.countByBoormiIdAndOrderCd(boormiId, OrderCd.COMPLETED)).willReturn(4L);
        given(orderRepository.sumCompletedDeliveryAmount(boormiId)).willReturn(14000L);
        given(deliveryRepository.aggregateSavingByBoormiBetween(eq(boormiId), any(), any()))
                .willReturn(List.of(savingAggregate(thisMonth, 2, 6000)));

        BoormiDashboardDto dashboard = boormiService.getDashboard(boormiId);

        // 누적: 시장 단가 5800원 × 4건 = 23200원, 실제 결제 14000원 → 9200원 절감
        assertThat(dashboard.completedCount()).isEqualTo(4);
        assertThat(dashboard.totalSavedAmount()).isEqualTo(9200);
        // 이번 달: 5800원 × 2건 = 11600원, 실제 결제 6000원 → 5600원 절감
        assertThat(dashboard.thisMonthCount()).isEqualTo(2);
        assertThat(dashboard.thisMonthSavedAmount()).isEqualTo(5600);
    }

    @Test
    void 실제_결제액이_시장단가보다_크면_절감액은_0이다() {
        UUID boormiId = UUID.randomUUID();
        YearMonth thisMonth = YearMonth.now();
        given(orderRepository.countByBoormiIdAndOrderCd(boormiId, OrderCd.COMPLETED)).willReturn(1L);
        given(orderRepository.sumCompletedDeliveryAmount(boormiId)).willReturn(9000L);
        given(deliveryRepository.aggregateSavingByBoormiBetween(eq(boormiId), any(), any()))
                .willReturn(List.of(savingAggregate(thisMonth, 1, 9000)));

        BoormiDashboardDto dashboard = boormiService.getDashboard(boormiId);

        assertThat(dashboard.totalSavedAmount()).isZero();
        assertThat(dashboard.thisMonthSavedAmount()).isZero();
    }

    @Test
    void 완료_주문이_없으면_모두_0이다() {
        UUID boormiId = UUID.randomUUID();
        given(orderRepository.countByBoormiIdAndOrderCd(boormiId, OrderCd.COMPLETED)).willReturn(0L);
        given(orderRepository.sumCompletedDeliveryAmount(boormiId)).willReturn(0L);
        given(deliveryRepository.aggregateSavingByBoormiBetween(eq(boormiId), any(), any())).willReturn(List.of());

        BoormiDashboardDto dashboard = boormiService.getDashboard(boormiId);

        assertThat(dashboard.completedCount()).isZero();
        assertThat(dashboard.totalSavedAmount()).isZero();
        assertThat(dashboard.thisMonthCount()).isZero();
        assertThat(dashboard.thisMonthSavedAmount()).isZero();
        assertThat(dashboard.monthOverMonthGrowthPercent()).isZero();
        assertThat(dashboard.recentSixMonths()).hasSize(6)
                .allSatisfy(monthly -> assertThat(monthly.savedAmount()).isZero());
    }

    @Test
    void 최근_6개월_추이는_기록없는_달을_0으로_채워_오름차순으로_반환한다() {
        UUID boormiId = UUID.randomUUID();
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        given(orderRepository.countByBoormiIdAndOrderCd(boormiId, OrderCd.COMPLETED)).willReturn(3L);
        given(orderRepository.sumCompletedDeliveryAmount(boormiId)).willReturn(9000L);
        given(deliveryRepository.aggregateSavingByBoormiBetween(eq(boormiId), any(), any()))
                .willReturn(List.of(savingAggregate(thisMonth, 2, 6000), savingAggregate(lastMonth, 1, 3000)));

        BoormiDashboardDto dashboard = boormiService.getDashboard(boormiId);

        assertThat(dashboard.recentSixMonths()).hasSize(6);
        assertThat(dashboard.recentSixMonths()).extracting(MonthlySavingDto::month)
                .containsExactly(thisMonth.minusMonths(5), thisMonth.minusMonths(4), thisMonth.minusMonths(3),
                        thisMonth.minusMonths(2), lastMonth, thisMonth);
        // 기록이 있는 달만 값이 차고 나머지는 0 — 지난달 2800원(5800 − 3000), 이번 달 5600원
        assertThat(dashboard.recentSixMonths()).extracting(MonthlySavingDto::savedAmount)
                .containsExactly(0L, 0L, 0L, 0L, 2800L, 5600L);
        // 2800원 → 5600원이므로 +100%
        assertThat(dashboard.monthOverMonthGrowthPercent()).isEqualTo(100);
    }

    @Test
    void 지난달_절감액이_0이면_증감률은_0이다() {
        UUID boormiId = UUID.randomUUID();
        YearMonth thisMonth = YearMonth.now();
        given(orderRepository.countByBoormiIdAndOrderCd(boormiId, OrderCd.COMPLETED)).willReturn(2L);
        given(orderRepository.sumCompletedDeliveryAmount(boormiId)).willReturn(6000L);
        given(deliveryRepository.aggregateSavingByBoormiBetween(eq(boormiId), any(), any()))
                .willReturn(List.of(savingAggregate(thisMonth, 2, 6000)));

        BoormiDashboardDto dashboard = boormiService.getDashboard(boormiId);

        assertThat(dashboard.thisMonthSavedAmount()).isEqualTo(5600);
        assertThat(dashboard.monthOverMonthGrowthPercent()).isZero();
    }

    private static MonthlySavingAggregate savingAggregate(YearMonth month, long count, long paidAmount) {
        return new MonthlySavingAggregate(month.getYear(), month.getMonthValue(), count, paidAmount);
    }
}
