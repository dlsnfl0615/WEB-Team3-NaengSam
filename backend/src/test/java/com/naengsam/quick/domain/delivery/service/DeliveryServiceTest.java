package com.naengsam.quick.domain.delivery.service;

import tools.jackson.databind.ObjectMapper;
import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import com.naengsam.quick.domain.address.service.KakaoDirectionsService;
import com.naengsam.quick.domain.delivery.dto.DeliveryDetailResponseDto;
import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationResponseDto;
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
import com.naengsam.quick.domain.order.entity.CancelerCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.UploadSessionService;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static com.naengsam.quick.domain.delivery.entity.DeliveryCd.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

/**
 * 배달 상태 전이 서비스 단위 테스트. 전이 가드 분기를 확인한다. 주문 단위 직렬화는 DeliveryRepository의 비관적 락 + 트랜잭션이
 * 보장하므로(목 기반 단위 테스트로는 재현 불가) 동시성은 통합 테스트 영역으로 둔다.
 */
class DeliveryServiceTest {

    private static final String PHOTO_KEY = "uploads/dreami/photo.png";

    private DeliveryRepository deliveryRepository;
    private PickupCertificationRepository pickupCertificationRepository;
    private DeliveryCertificationRepository deliveryCertificationRepository;
    private SseService sseService;
    private UploadSessionService uploadSessionService;
    private UserService userService;
    private OrderService orderService;
    private KakaoDirectionsService kakaoDirectionsService;
    private ApplicationEventPublisher eventPublisher;
    private DeliveryService deliveryService;

    // findByOrderId가 같은 Delivery 인스턴스를 돌려주도록 등록해 둔다(서비스가 이 객체를 변경하면 테스트에서 바로 관찰된다).
    private final Map<UUID, Delivery> registeredDeliveries = new HashMap<>();

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        pickupCertificationRepository = mock(PickupCertificationRepository.class);
        deliveryCertificationRepository = mock(DeliveryCertificationRepository.class);
        sseService = mock(SseService.class);
        uploadSessionService = mock(UploadSessionService.class);
        userService = mock(UserService.class);
        orderService = mock(OrderService.class);
        kakaoDirectionsService = mock(KakaoDirectionsService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        deliveryService = new DeliveryService(deliveryRepository, pickupCertificationRepository,
                deliveryCertificationRepository, sseService, uploadSessionService,
                userService, orderService, kakaoDirectionsService, eventPublisher, new ObjectMapper());
        // 기본값: 미등록 주문은 빈 Optional, 사진은 정상 업로드된 것으로 간주(checkUpload 통과).
        given(deliveryRepository.findByOrderId(any())).willReturn(Optional.empty());
        given(deliveryRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(uploadSessionService.checkUpload(any(), any(), any(), any())).willReturn(true);
        // 기본값: 위치 갱신 때 도는 '드리미→픽업지' 계산이 다른 테스트를 깨지 않도록, 주문은 좌표 있는 목을,
        // 카카오는 정상 경로를 돌려준다(실제 HTTP 미호출). 계산·실패를 검증하는 테스트에서만 getRoute를 개별 스텁한다.
        Orders defaultOrder = orderWithPickup("37.40000000", "127.00000000", 20);
        given(orderService.getOrder(any())).willReturn(defaultOrder);
        KakaoDirectionsResponseDto.Route defaultRoute = routeWith(60);
        given(kakaoDirectionsService.getRoute(any(), any())).willReturn(defaultRoute);
    }

    // 픽업지 좌표와 delivery_eta(분)를 가진 주문 목. '드리미→픽업지' 경로/배송완료예상시간 계산 경로에서 쓴다.
    private Orders orderWithPickup(String originLat, String originLng, int deliveryEtaMinutes) {
        Orders order = mock(Orders.class);
        given(order.getOriginLatitude()).willReturn(new BigDecimal(originLat));
        given(order.getOriginLongitude()).willReturn(new BigDecimal(originLng));
        given(order.getDeliveryEta()).willReturn(deliveryEtaMinutes);
        return order;
    }

    // totalTime(초)와 좌표 두 점을 가진 카카오 도보 경로 목 응답. RoutePointDto.from 이 [경도,위도]→(위도,경도)로 정규화한다.
    private KakaoDirectionsResponseDto.Route routeWith(int totalTimeSeconds) {
        KakaoDirectionsResponseDto.Path path = new KakaoDirectionsResponseDto.Path(
                new double[][] {{127.0, 37.5}, {127.1, 37.6}});
        KakaoDirectionsResponseDto.Step step = new KakaoDirectionsResponseDto.Step(
                new KakaoDirectionsResponseDto.StepProperties(1000, "", totalTimeSeconds, 127.0, 37.5), path);
        KakaoDirectionsResponseDto.Leg leg = new KakaoDirectionsResponseDto.Leg(
                new KakaoDirectionsResponseDto.LegProperties(1000, totalTimeSeconds),
                new KakaoDirectionsResponseDto.Step[] {step});
        return new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(1000, totalTimeSeconds),
                new KakaoDirectionsResponseDto.Leg[] {leg});
    }

    // ===== 픽스처 =====

    // 주어진 상태로 배달 한 건을 등록(findByOrderId 스텁)하고 orderId를 돌려준다.
    private UUID registerDelivery(DeliveryCd status) {
        return registerDeliveryWith(status, UUID.randomUUID(), UUID.randomUUID());
    }

    // 지정한 dreamiId/boormiId와 상태로 배달을 등록하고 orderId를 돌려준다(SSE 수신자 검증용).
    private UUID registerDeliveryWith(DeliveryCd status, UUID dreamiId, UUID boormiId) {
        UUID orderId = UUID.randomUUID();
        Delivery delivery = Delivery.create(orderId, dreamiId, boormiId);
        ReflectionTestUtils.setField(delivery, "deliveryCd", status);
        registeredDeliveries.put(orderId, delivery);
        given(deliveryRepository.findByOrderId(orderId)).willReturn(Optional.of(delivery));
        return orderId;
    }

    private DeliveryCd statusOf(UUID orderId) {
        return registeredDeliveries.get(orderId).getDeliveryCd();
    }

    // 등록된 주문의 배정 드리미 본인이 유효한 사진 key로 픽업/배달 완료를 요청하는 정상 경로 헬퍼.
    private DeliveryStatusResponseDto pickupFinish(UUID orderId) {
        return deliveryService.pickupFinishByDreami(
                orderId, registeredDeliveries.get(orderId).getDreamiId(), PHOTO_KEY);
    }

    private DeliveryStatusResponseDto finish(UUID orderId) {
        return deliveryService.finishDelivery(
                orderId, registeredDeliveries.get(orderId).getDreamiId(), PHOTO_KEY);
    }

    // 등록된 주문의 배정 드리미/접수 부르미 본인이 취소를 요청하는 정상 경로 헬퍼(소유권 검증 통과).
    private DeliveryStatusResponseDto cancelByDreami(UUID orderId) {
        return deliveryService.cancelByDreami(orderId, registeredDeliveries.get(orderId).getDreamiId());
    }

    private DeliveryStatusResponseDto cancelByBoormi(UUID orderId) {
        return deliveryService.cancelByBoormi(orderId, registeredDeliveries.get(orderId).getBoormiId());
    }

    // userId·eventType으로 DeliveryNotificationEvent가 발행됐는지만 확인한다(실제 전송은 sendAfterCommit이 맡는다).
    // 람다 파라미터 타입을 명시해야 ApplicationEventPublisher의 publishEvent(ApplicationEvent)/publishEvent(Object)
    // 오버로드 중 Object 쪽으로 확정된다(명시하지 않으면 ApplicationEvent로 추론돼 컴파일 에러가 난다).
    private void assertPublished(UUID userId, DeliveryEventType type) {
        verify(eventPublisher).publishEvent(argThat((DeliveryNotificationEvent event) ->
                event.userId().equals(userId) && event.eventType() == type));
    }

    private BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    private DreamiLocationRequest location(String latitude, String longitude) {
        return new DreamiLocationRequest(new BigDecimal(latitude), new BigDecimal(longitude));
    }

    private DreamiLocationRequest location(String latitude, String longitude, boolean includeRoute) {
        return new DreamiLocationRequest(new BigDecimal(latitude), new BigDecimal(longitude), includeRoute);
    }

    // 세 취소 경로를 orderId 하나로 실행하는 헬퍼. 드리미/부르미는 등록된 소유자 본인으로 호출해 소유권 검증을 통과시키고,
    // 그 이후의 상태 가드 분기만 검증되도록 한다.
    private List<Function<UUID, DeliveryStatusResponseDto>> cancelOperations() {
        return List.of(
                this::cancelByDreami,
                this::cancelByBoormi,
                deliveryService::cancelByAdmin);
    }

    // ===== 배달 시작 =====

    // 주문자는 활성 드리미가 아니고(false), 배달자는 활성 드리미(true)인 정상 역할 상태를 스텁한다.
    private void stubValidRoles(UUID boormiId, UUID dreamiId) {
        given(userService.getUserInfo(boormiId)).willReturn(new UserDto(boormiId, "b@t.com", "부르미", false));
        given(userService.getUserInfo(dreamiId)).willReturn(new UserDto(dreamiId, "d@t.com", "드리미", true));
    }

    private void stubOrderStatus(UUID orderId, OrderCd orderCd) {
        Orders order = mock(Orders.class);
        given(order.getOrderCd()).willReturn(orderCd);
        given(orderService.getOrder(orderId)).willReturn(order);
    }

    @Test
    void 배달시작하면_PICKUP_NORMAL로_저장된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        stubOrderStatus(orderId, OrderCd.IN_PROGRESS);
        stubValidRoles(boormiId, dreamiId);

        deliveryService.startDelivery(orderId, dreamiId, boormiId);

        ArgumentCaptor<Delivery> captor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveryRepository).save(captor.capture());
        Delivery saved = captor.getValue();
        assertThat(saved.getDeliveryCd()).isEqualTo(PICKUP_NORMAL);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getDreamiId()).isEqualTo(dreamiId);
        assertThat(saved.getBoormiId()).isEqualTo(boormiId);
    }

    @Test
    void 배달시작하면_부르미에게_DELIVERY_STARTED_BOORMI_SSE전송() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        stubOrderStatus(orderId, OrderCd.IN_PROGRESS);
        stubValidRoles(boormiId, dreamiId);

        deliveryService.startDelivery(orderId, dreamiId, boormiId);

        assertPublished(boormiId, DeliveryEventType.DELIVERY_STARTED_BOORMI);
    }

    @Test
    void 배달시작하면_드리미에게_DELIVERY_STARTED_DREAMI_SSE전송() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        stubOrderStatus(orderId, OrderCd.IN_PROGRESS);
        stubValidRoles(boormiId, dreamiId);

        deliveryService.startDelivery(orderId, dreamiId, boormiId);

        assertPublished(dreamiId, DeliveryEventType.DELIVERY_STARTED_DREAMI);
    }

    // ===== 배달 상세 조회 =====

    @Test
    void 배달상세조회하면_락없는_조회메서드를_쓴다() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Delivery delivery = Delivery.create(orderId, dreamiId, boormiId);
        given(deliveryRepository.findByOrderIdWithoutLock(orderId)).willReturn(Optional.of(delivery));
        Orders order = mock(Orders.class);
        given(order.getOrderId()).willReturn(orderId);
        given(orderService.getOrder(orderId)).willReturn(order);

        DeliveryDetailResponseDto result = deliveryService.getDeliveryDetail(orderId, boormiId);

        assertThat(result.orderId()).isEqualTo(orderId);
        verify(deliveryRepository, never()).findByOrderId(any()); // 비관적 락(FOR UPDATE)은 readOnly 트랜잭션에서 못 씀
    }

    @Test
    void 배달상세조회시_주문의_route_path_JSON을_좌표목록으로_복원한다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Delivery delivery = Delivery.create(orderId, UUID.randomUUID(), boormiId);
        given(deliveryRepository.findByOrderIdWithoutLock(orderId)).willReturn(Optional.of(delivery));
        Orders order = mock(Orders.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getRoutePath())
                .willReturn("[{\"latitude\":37.5,\"longitude\":127.0},{\"latitude\":37.6,\"longitude\":127.1}]");
        given(orderService.getOrder(orderId)).willReturn(order);

        DeliveryDetailResponseDto result = deliveryService.getDeliveryDetail(orderId, boormiId);

        assertThat(result.routePath()).hasSize(2);
        assertThat(result.routePath().getFirst().latitude()).isEqualTo(37.5);
        assertThat(result.routePath().getFirst().longitude()).isEqualTo(127.0);
    }

    @Test
    void 배달상세조회시_route_path가_없으면_빈_경로를_반환한다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Delivery delivery = Delivery.create(orderId, UUID.randomUUID(), boormiId);
        given(deliveryRepository.findByOrderIdWithoutLock(orderId)).willReturn(Optional.of(delivery));
        Orders order = mock(Orders.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getRoutePath()).willReturn(null);
        given(orderService.getOrder(orderId)).willReturn(order);

        DeliveryDetailResponseDto result = deliveryService.getDeliveryDetail(orderId, boormiId);

        assertThat(result.routePath()).isEmpty();
    }

    @Test
    void 배달상세조회시_배달의_route_path와_배송완료예상시간도_함께_내려준다() {
        UUID boormiId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        LocalDateTime completion = LocalDateTime.now().plusMinutes(25);
        Delivery delivery = Delivery.create(orderId, UUID.randomUUID(), boormiId);
        ReflectionTestUtils.setField(delivery, "routePath",
                "[{\"latitude\":37.4,\"longitude\":127.0},{\"latitude\":37.5,\"longitude\":127.1}]");
        ReflectionTestUtils.setField(delivery, "estimatedCompletionDtm", completion);
        given(deliveryRepository.findByOrderIdWithoutLock(orderId)).willReturn(Optional.of(delivery));
        Orders order = mock(Orders.class);
        given(order.getOrderId()).willReturn(orderId);
        given(orderService.getOrder(orderId)).willReturn(order);

        DeliveryDetailResponseDto result = deliveryService.getDeliveryDetail(orderId, boormiId);

        assertThat(result.deliveryRoutePath()).hasSize(2);
        assertThat(result.deliveryRoutePath().getFirst().latitude()).isEqualTo(37.4);
        assertThat(result.estimatedCompletionTime()).isEqualTo(completion);
    }

    // ===== 첫 위치 전송 시 '드리미→픽업지' 경로·배송완료예상시간 계산 =====

    @Test
    void 첫_위치_전송시_드리미픽업지_경로와_배송완료예상시간을_계산해_저장한다() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);
        Orders order = orderWithPickup("37.50000000", "127.05000000", 20);
        given(orderService.getOrder(orderId)).willReturn(order);
        given(kakaoDirectionsService.getRoute(any(), any())).willReturn(routeWith(300)); // 300초 = 5분

        LocalDateTime before = LocalDateTime.now();
        DreamiLocationResponseDto response =
                deliveryService.updateDreamiLocation(orderId, location("37.40000000", "127.00000000"));
        LocalDateTime after = LocalDateTime.now();

        Delivery saved = registeredDeliveries.get(orderId);
        assertThat(saved.getRoutePath()).contains("latitude");
        // 드리미→픽업지 5분 + 주문 delivery_eta 20분 = 25분 뒤(계산 시각의 now 기준)
        assertThat(saved.getEstimatedCompletionDtm())
                .isBetween(before.plusMinutes(25), after.plusMinutes(25));
        verify(kakaoDirectionsService).getRoute(any(), any());

        // 응답에도 방금 계산된 경로·배송완료예상시간이 담겨 나간다(프론트가 재조회 없이 바로 반영).
        assertThat(response.deliveryRoutePath()).hasSize(2);
        assertThat(response.estimatedCompletionTime()).isEqualTo(saved.getEstimatedCompletionDtm());
    }

    @Test
    void 이미_경로가_계산돼있으면_카카오를_다시_호출하지_않는다() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);
        Delivery delivery = registeredDeliveries.get(orderId);
        ReflectionTestUtils.setField(delivery, "routePath", "[{\"latitude\":37.4,\"longitude\":127.0}]");

        DreamiLocationResponseDto response =
                deliveryService.updateDreamiLocation(orderId, location("37.40000000", "127.00000000"));

        // 이미 저장돼 있으므로 카카오를 다시 호출하지 않고, 요청이 경로를 원했으므로(기본값) 저장된 경로를 그대로 돌려준다.
        verify(kakaoDirectionsService, never()).getRoute(any(), any());
        assertThat(response.deliveryRoutePath()).hasSize(1);
    }

    @Test
    void 경로를_원하지_않으면_includeRoute_false_응답에_경로와_완료시간을_담지_않는다() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);
        Delivery delivery = registeredDeliveries.get(orderId);
        ReflectionTestUtils.setField(delivery, "routePath", "[{\"latitude\":37.4,\"longitude\":127.0}]");
        ReflectionTestUtils.setField(delivery, "estimatedCompletionDtm", LocalDateTime.now().plusMinutes(25));

        DreamiLocationResponseDto response = deliveryService.updateDreamiLocation(
                orderId, location("37.40000000", "127.00000000", false));

        // 클라이언트가 경로를 원치 않으면(이미 받음) 좌표 배열을 중복 전송하지 않는다.
        assertThat(response.deliveryRoutePath()).isNull();
        assertThat(response.estimatedCompletionTime()).isNull();
        verify(kakaoDirectionsService, never()).getRoute(any(), any());
    }

    @Test
    void 배달중_상태의_위치갱신은_픽업경로를_계산하지_않는다() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DELIVERING, dreamiId, boormiId);

        deliveryService.updateDreamiLocation(orderId, location("37.40000000", "127.00000000"));

        verify(kakaoDirectionsService, never()).getRoute(any(), any());
        assertThat(registeredDeliveries.get(orderId).getRoutePath()).isNull();
    }

    @Test
    void 카카오_실패시_위치는_갱신되고_경로와_완료시간은_null로_남는다() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);
        Orders order = orderWithPickup("37.50000000", "127.05000000", 20);
        given(orderService.getOrder(orderId)).willReturn(order);
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willThrow(new BusinessException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT));

        deliveryService.updateDreamiLocation(orderId, location("37.40000000", "127.00000000"));

        Delivery saved = registeredDeliveries.get(orderId);
        assertThat(saved.getCurrentLatitude()).isEqualByComparingTo("37.40000000");
        assertThat(saved.getRoutePath()).isNull();
        assertThat(saved.getEstimatedCompletionDtm()).isNull();
        assertPublished(boormiId, DeliveryEventType.DELIVERY_LOCATION); // 위치 갱신 SSE는 정상 발행
    }

    @Test
    void 배달상세조회시_부르미도_드리미도_아니면_NOT_RESOURCE_OWNER_예외() {
        UUID orderId = UUID.randomUUID();
        Delivery delivery = Delivery.create(orderId, UUID.randomUUID(), UUID.randomUUID());
        given(deliveryRepository.findByOrderIdWithoutLock(orderId)).willReturn(Optional.of(delivery));
        given(orderService.getOrder(orderId)).willReturn(mock(Orders.class));

        Throwable thrown = catchThrowable(() -> deliveryService.getDeliveryDetail(orderId, UUID.randomUUID()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    void 배달시작_담당드리미가_비활성이면_DREAMI_NOT_ACTIVATED_예외() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        stubOrderStatus(orderId, OrderCd.IN_PROGRESS);
        given(userService.getUserInfo(boormiId)).willReturn(new UserDto(boormiId, "b@t.com", "부르미", false));
        given(userService.getUserInfo(dreamiId)).willReturn(new UserDto(dreamiId, "d@t.com", "드리미", false));

        Throwable thrown = catchThrowable(() -> deliveryService.startDelivery(orderId, dreamiId, boormiId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DREAMI_NOT_ACTIVATED);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void 배달시작_주문이_진행중이_아니면_DELIVERY_START_NOT_ALLOWED_예외() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        stubOrderStatus(orderId, OrderCd.PENDING_BOORMI_CONFIRMATION);

        Throwable thrown = catchThrowable(() -> deliveryService.startDelivery(orderId, dreamiId, boormiId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_START_NOT_ALLOWED);
        verify(userService, never()).getUserInfo(any());
        verify(deliveryRepository, never()).save(any());
    }

    // ===== SSE 알림 =====

    @Test
    void 위치갱신되면_부르미에게_DELIVERY_LOCATION_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        deliveryService.updateDreamiLocation(orderId, location("37.5", "127.0"));

        assertPublished(boormiId, DeliveryEventType.DELIVERY_LOCATION);
    }

    @Test
    void 픽업완료되면_부르미에게_DELIVERY_DELIVERING_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        pickupFinish(orderId);

        assertPublished(boormiId, DeliveryEventType.DELIVERY_DELIVERING);
    }

    @Test
    void 배달완료되면_부르미에게_DELIVERY_COMPLETED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DELIVERING, dreamiId, boormiId);

        finish(orderId);

        assertPublished(boormiId, DeliveryEventType.DELIVERY_COMPLETED);
    }

    @Test
    void 드리미취소되면_부르미에게_DELIVERY_CANCELLED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        cancelByDreami(orderId);

        assertPublished(boormiId, DeliveryEventType.DELIVERY_CANCELLED);
    }

    @Test
    void 부르미취소되면_드리미에게_DELIVERY_CANCELLED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        cancelByBoormi(orderId);

        assertPublished(dreamiId, DeliveryEventType.DELIVERY_CANCELLED);
    }

    @Test
    void 관리자취소되면_부르미와_드리미에게_DELIVERY_CANCELLED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        deliveryService.cancelByAdmin(orderId);

        assertPublished(boormiId, DeliveryEventType.DELIVERY_CANCELLED);
        assertPublished(dreamiId, DeliveryEventType.DELIVERY_CANCELLED);
    }

    @Test
    void 커밋후리스너가_이벤트를_그대로_SseService로_전달한다() {
        UUID userId = UUID.randomUUID();
        DeliveryStatusResponseDto payload =
                new DeliveryStatusResponseDto(UUID.randomUUID(), PICKUP_NORMAL, null, "메시지");
        DeliveryNotificationEvent event =
                new DeliveryNotificationEvent(userId, DeliveryEventType.DELIVERY_STARTED_BOORMI, payload);

        deliveryService.sendAfterCommit(event);

        verify(sseService).send(userId, DeliveryEventType.DELIVERY_STARTED_BOORMI, payload);
    }

    @Test
    void 부르미취소된주문에_드리미가_위치갱신하면_DELIVERY_ALREADY_CANCELLED_예외() {
        UUID orderId = registerDelivery(PICKUP_CANCELLED_BY_BOORMI);

        Throwable thrown = catchThrowable(
                () -> deliveryService.updateDreamiLocation(orderId, location("37.5", "127.0")));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
    }

    // ===== 상태 전이 단위 테스트 =====

    @Test
    void 픽업완료_정상이면_DELIVERING으로_전이() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        DeliveryStatusResponseDto result = pickupFinish(orderId);

        assertThat(result.message()).isEqualTo("픽업 완료");
        assertThat(result.status()).isEqualTo(DELIVERING);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
    }

    @Test
    void 등록되지않은_배달이면_DELIVERY_NOT_FOUND_예외() {
        UUID orderId = UUID.randomUUID();

        Throwable thrown = catchThrowable(
                () -> deliveryService.pickupFinishByDreami(orderId, UUID.randomUUID(), PHOTO_KEY));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_NOT_FOUND);
    }

    @Test
    void 픽업완료_이미_취소된주문이면_DELIVERY_ALREADY_CANCELLED_예외() {
        for (DeliveryCd cancelledStatus : List.of(
                DeliveryCd.PICKUP_CANCELLED_BY_BOORMI,
                DeliveryCd.PICKUP_CANCELLED_BY_DREAMI,
                DeliveryCd.PICKUP_CANCELLED_BY_ADMIN)) {
            UUID orderId = registerDelivery(cancelledStatus);

            Throwable thrown = catchThrowable(() -> pickupFinish(orderId));

            assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            assertThat(statusOf(orderId)).isEqualTo(cancelledStatus);
        }
    }

    @Test
    void 픽업완료_이미_처리된단계면_STEP_ALREADY_VERIFIED_예외() {
        UUID orderId = registerDelivery(DELIVERING);

        Throwable thrown = catchThrowable(() -> pickupFinish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.STEP_ALREADY_VERIFIED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
    }

    @Test
    void 픽업완료_이미_배달완료된주문이면_DELIVERY_ALREADY_COMPLETED_예외() {
        UUID orderId = registerDelivery(DELIVERED);

        Throwable thrown = catchThrowable(() -> pickupFinish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
    }

    @Test
    void 드리미취소_정상이면_PICKUP_CANCELLED_BY_DREAMI로_전이() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        DeliveryStatusResponseDto result = cancelByDreami(orderId);

        assertThat(result.message()).isEqualTo("픽업 취소 완료");
        assertThat(result.status()).isEqualTo(PICKUP_CANCELLED_BY_DREAMI);
        assertThat(statusOf(orderId)).isEqualTo(PICKUP_CANCELLED_BY_DREAMI);
    }

    @Test
    void 배달완료_배달중이면_DELIVERED로_전이() {
        UUID orderId = registerDelivery(DELIVERING);

        DeliveryStatusResponseDto result = finish(orderId);

        assertThat(result.message()).isEqualTo("드리미에게_완료");
        assertThat(result.status()).isEqualTo(DELIVERED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
    }

    // ===== 주문(Orders) 상태 동기화 =====

    @Test
    void 배달완료되면_주문을_COMPLETED로_전이시킨다() {
        UUID orderId = registerDelivery(DELIVERING);

        finish(orderId);

        verify(orderService).complete(orderId);
    }

    @Test
    void 픽업완료는_주문상태를_바꾸지_않는다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        pickupFinish(orderId);

        verify(orderService, never()).complete(any());
        verify(orderService, never()).cancel(any(UUID.class), any());
    }

    @Test
    void 드리미취소되면_주문을_DREAMI_취소자로_CANCELLED_전이시킨다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        cancelByDreami(orderId);

        verify(orderService).cancel(orderId, CancelerCd.DREAMI);
    }

    @Test
    void 부르미취소되면_주문을_BOORMI_취소자로_CANCELLED_전이시킨다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        cancelByBoormi(orderId);

        verify(orderService).cancel(orderId, CancelerCd.BOORMI);
    }

    @Test
    void 관리자취소되면_주문을_ADMIN_취소자로_CANCELLED_전이시킨다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        deliveryService.cancelByAdmin(orderId);

        verify(orderService).cancel(orderId, CancelerCd.ADMIN);
    }

    @Test
    void 드리미취소_배정되지않은_드리미면_NOT_ASSIGNED_DREAMI_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID otherDreamiId = UUID.randomUUID();

        Throwable thrown = catchThrowable(() -> deliveryService.cancelByDreami(orderId, otherDreamiId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
        verify(orderService, never()).cancel(eq(orderId), any());
    }

    @Test
    void 부르미취소_주문의_부르미가_아니면_NOT_ORDER_BOORMI_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID otherBoormiId = UUID.randomUUID();

        Throwable thrown = catchThrowable(() -> deliveryService.cancelByBoormi(orderId, otherBoormiId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.NOT_ORDER_BOORMI);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
        verify(orderService, never()).cancel(eq(orderId), any());
    }

    @Test
    void 취소가_거부되면_주문상태를_바꾸지_않는다() {
        for (Function<UUID, DeliveryStatusResponseDto> cancelOperation : cancelOperations()) {
            UUID orderId = registerDelivery(DELIVERING); // 배달중이면 취소 불가

            catchThrowable(() -> cancelOperation.apply(orderId));

            verify(orderService, never()).cancel(eq(orderId), any());
        }
    }

    // ===== 사진 인증 =====

    @Test
    void 픽업완료_사진이_업로드안됐으면_FILE_NOT_FOUND_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID dreamiId = registeredDeliveries.get(orderId).getDreamiId();
        willThrow(new BusinessException(UploadErrorCode.FILE_NOT_FOUND))
                .given(uploadSessionService)
                .checkUpload(UploadPurpose.PICKUP_CERTIFICATION_IMAGE, dreamiId, orderId, PHOTO_KEY);

        Throwable thrown = catchThrowable(() -> pickupFinish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(UploadErrorCode.FILE_NOT_FOUND);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
    }

    @Test
    void 배달완료_사진이_업로드안됐으면_FILE_NOT_FOUND_예외() {
        UUID orderId = registerDelivery(DELIVERING);
        UUID dreamiId = registeredDeliveries.get(orderId).getDreamiId();
        willThrow(new BusinessException(UploadErrorCode.FILE_NOT_FOUND))
                .given(uploadSessionService)
                .checkUpload(UploadPurpose.DELIVERY_CERTIFICATION_IMAGE, dreamiId, orderId, PHOTO_KEY);

        Throwable thrown = catchThrowable(() -> finish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(UploadErrorCode.FILE_NOT_FOUND);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
    }

    @Test
    void 픽업완료_남의_key를_제출하면_KEY_OWNER_MISMATCH_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID dreamiId = registeredDeliveries.get(orderId).getDreamiId();
        willThrow(new BusinessException(UploadErrorCode.KEY_OWNER_MISMATCH))
                .given(uploadSessionService)
                .checkUpload(UploadPurpose.PICKUP_CERTIFICATION_IMAGE, dreamiId, orderId, PHOTO_KEY);

        Throwable thrown = catchThrowable(
                () -> deliveryService.pickupFinishByDreami(orderId, dreamiId, PHOTO_KEY));

        assertThat(errorCodeOf(thrown)).isEqualTo(UploadErrorCode.KEY_OWNER_MISMATCH);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
    }

    @Test
    void 픽업완료_배정되지않은_드리미면_NOT_ASSIGNED_DREAMI_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID otherDreamiId = UUID.randomUUID();

        Throwable thrown = catchThrowable(
                () -> deliveryService.pickupFinishByDreami(orderId, otherDreamiId, PHOTO_KEY));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
    }

    @Test
    void 배달완료_배정되지않은_드리미면_NOT_ASSIGNED_DREAMI_예외() {
        UUID orderId = registerDelivery(DELIVERING);
        UUID otherDreamiId = UUID.randomUUID();

        Throwable thrown = catchThrowable(
                () -> deliveryService.finishDelivery(orderId, otherDreamiId, PHOTO_KEY));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.NOT_ASSIGNED_DREAMI);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
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
    void 위치갱신_좌표를_소수점8자리_HALF_UP으로_저장한다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        deliveryService.updateDreamiLocation(orderId, location("37.123456789", "127.1"));

        BigDecimal storedLatitude = registeredDeliveries.get(orderId).getCurrentLatitude();
        BigDecimal storedLongitude = registeredDeliveries.get(orderId).getCurrentLongitude();
        assertThat(storedLatitude).isEqualByComparingTo(new BigDecimal("37.12345679"));
        assertThat(storedLatitude.scale()).isEqualTo(8);
        assertThat(storedLongitude).isEqualByComparingTo(new BigDecimal("127.10000000"));
        assertThat(storedLongitude.scale()).isEqualTo(8);
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
    void 배달완료_픽업전이면_DELIVERY_COMPLETION_NOT_ALLOWED_BEFORE_PICKUP_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        Throwable thrown = catchThrowable(() -> finish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_COMPLETION_NOT_ALLOWED_BEFORE_PICKUP);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
    }

    @Test
    void 배달완료_이미_취소된주문이면_DELIVERY_ALREADY_CANCELLED_예외() {
        for (DeliveryCd cancelledStatus : List.of(
                DeliveryCd.PICKUP_CANCELLED_BY_BOORMI,
                DeliveryCd.PICKUP_CANCELLED_BY_DREAMI,
                DeliveryCd.PICKUP_CANCELLED_BY_ADMIN)) {
            UUID orderId = registerDelivery(cancelledStatus);

            Throwable thrown = catchThrowable(() -> finish(orderId));

            assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_CANCELLED);
            assertThat(statusOf(orderId)).isEqualTo(cancelledStatus);
        }
    }

    @Test
    void 배달완료_이미_완료된주문이면_DELIVERY_ALREADY_COMPLETED_예외() {
        UUID orderId = registerDelivery(DELIVERED);

        Throwable thrown = catchThrowable(() -> finish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_ALREADY_COMPLETED);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERED);
    }

    // ===== 인증 행 저장 (비대면 인증) =====

    @Test
    void 픽업완료_성공하면_PickupCertification이_저장된다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        pickupFinish(orderId);

        ArgumentCaptor<PickupCertification> captor = ArgumentCaptor.forClass(PickupCertification.class);
        verify(pickupCertificationRepository).save(captor.capture());
        PickupCertification saved = captor.getValue();
        assertThat(saved.isContact()).isFalse();
        assertThat(saved.getImageKey()).isEqualTo(PHOTO_KEY);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getSignKey()).isNull();
        assertThat(saved.getSubmittedDtm())
                .isEqualTo(registeredDeliveries.get(orderId).getPickedUpDtm());
    }

    @Test
    void 배달완료_성공하면_DeliveryCertification이_저장된다() {
        UUID orderId = registerDelivery(DELIVERING);

        finish(orderId);

        ArgumentCaptor<DeliveryCertification> captor = ArgumentCaptor.forClass(DeliveryCertification.class);
        verify(deliveryCertificationRepository).save(captor.capture());
        DeliveryCertification saved = captor.getValue();
        assertThat(saved.isContact()).isFalse();
        assertThat(saved.getImageKey()).isEqualTo(PHOTO_KEY);
        assertThat(saved.getDeliveryId())
                .isEqualTo(registeredDeliveries.get(orderId).getDeliveryId());
        assertThat(saved.getSignKey()).isNull();
        assertThat(saved.getSubmittedDtm())
                .isEqualTo(registeredDeliveries.get(orderId).getDeliveryEndDtm());
    }

    @Test
    void 픽업완료_사진이없으면_PickupCertification을_저장하지_않는다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID dreamiId = registeredDeliveries.get(orderId).getDreamiId();
        willThrow(new BusinessException(UploadErrorCode.FILE_NOT_FOUND))
                .given(uploadSessionService)
                .checkUpload(UploadPurpose.PICKUP_CERTIFICATION_IMAGE, dreamiId, orderId, PHOTO_KEY);

        catchThrowable(() -> pickupFinish(orderId));

        verify(pickupCertificationRepository, never()).save(any());
    }

    @Test
    void 픽업완료_배정되지않은_드리미면_PickupCertification을_저장하지_않는다() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        catchThrowable(() -> deliveryService.pickupFinishByDreami(orderId, UUID.randomUUID(), PHOTO_KEY));

        verify(pickupCertificationRepository, never()).save(any());
    }

    @Test
    void 배달완료_사진이없으면_DeliveryCertification을_저장하지_않는다() {
        UUID orderId = registerDelivery(DELIVERING);
        UUID dreamiId = registeredDeliveries.get(orderId).getDreamiId();
        willThrow(new BusinessException(UploadErrorCode.FILE_NOT_FOUND))
                .given(uploadSessionService)
                .checkUpload(UploadPurpose.DELIVERY_CERTIFICATION_IMAGE, dreamiId, orderId, PHOTO_KEY);

        catchThrowable(() -> finish(orderId));

        verify(deliveryCertificationRepository, never()).save(any());
    }

    @Test
    void 배달완료_배정되지않은_드리미면_DeliveryCertification을_저장하지_않는다() {
        UUID orderId = registerDelivery(DELIVERING);

        catchThrowable(() -> deliveryService.finishDelivery(orderId, UUID.randomUUID(), PHOTO_KEY));

        verify(deliveryCertificationRepository, never()).save(any());
    }
}
