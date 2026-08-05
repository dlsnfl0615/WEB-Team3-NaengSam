package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.dto.DeliveryStatusResponseDto;
import com.naengsam.quick.domain.delivery.dto.DreamiLocationRequest;
import com.naengsam.quick.domain.delivery.entity.Delivery;
import com.naengsam.quick.domain.delivery.entity.DeliveryCd;
import com.naengsam.quick.domain.delivery.entity.DeliveryCertification;
import com.naengsam.quick.domain.delivery.entity.PickupCertification;
import com.naengsam.quick.domain.delivery.event.DeliveryEventType;
import com.naengsam.quick.domain.delivery.exception.DeliveryErrorCode;
import com.naengsam.quick.domain.delivery.repository.DeliveryCertificationRepository;
import com.naengsam.quick.domain.delivery.repository.DeliveryRepository;
import com.naengsam.quick.domain.delivery.repository.PickupCertificationRepository;
import com.naengsam.quick.domain.upload.entity.UploadPurpose;
import com.naengsam.quick.domain.upload.exception.UploadErrorCode;
import com.naengsam.quick.domain.upload.service.S3PresignService;
import com.naengsam.quick.domain.upload.service.UploadSessionService;
import com.naengsam.quick.domain.user.dto.UserDto;
import com.naengsam.quick.domain.user.service.UserService;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import com.naengsam.quick.global.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
    private S3PresignService s3PresignService;
    private UploadSessionService uploadSessionService;
    private UserService userService;
    private DeliveryService deliveryService;

    // findByOrderId가 같은 Delivery 인스턴스를 돌려주도록 등록해 둔다(서비스가 이 객체를 변경하면 테스트에서 바로 관찰된다).
    private final Map<UUID, Delivery> registeredDeliveries = new HashMap<>();

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(DeliveryRepository.class);
        pickupCertificationRepository = mock(PickupCertificationRepository.class);
        deliveryCertificationRepository = mock(DeliveryCertificationRepository.class);
        sseService = mock(SseService.class);
        s3PresignService = mock(S3PresignService.class);
        uploadSessionService = mock(UploadSessionService.class);
        userService = mock(UserService.class);
        deliveryService = new DeliveryService(deliveryRepository, pickupCertificationRepository,
                deliveryCertificationRepository, sseService, s3PresignService, uploadSessionService,
                userService);
        // 기본값: 미등록 주문은 빈 Optional, 사진은 존재. 스코프 검증(validateScope)은 void라 기본 no-op(통과).
        given(deliveryRepository.findByOrderId(any())).willReturn(Optional.empty());
        given(s3PresignService.isFileUploaded(any())).willReturn(true);
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

    // 주문자는 활성 드리미가 아니고(false), 배달자는 활성 드리미(true)인 정상 역할 상태를 스텁한다.
    private void stubValidRoles(UUID boormiId, UUID dreamiId) {
        given(userService.getUserInfo(boormiId)).willReturn(new UserDto(boormiId, "b@t.com", "부르미", false));
        given(userService.getUserInfo(dreamiId)).willReturn(new UserDto(dreamiId, "d@t.com", "드리미", true));
    }

    @Test
    void 배달시작하면_PICKUP_NORMAL로_저장된다() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
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
    void 배달시작_담당드리미가_비활성이면_DREAMI_NOT_ACTIVATED_예외() {
        UUID orderId = UUID.randomUUID();
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        given(userService.getUserInfo(boormiId)).willReturn(new UserDto(boormiId, "b@t.com", "부르미", false));
        given(userService.getUserInfo(dreamiId)).willReturn(new UserDto(dreamiId, "d@t.com", "드리미", false));

        Throwable thrown = catchThrowable(() -> deliveryService.startDelivery(orderId, dreamiId, boormiId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DREAMI_NOT_ACTIVATED);
        verify(deliveryRepository, never()).save(any());
    }

    // ===== SSE 알림 =====

    @Test
    void 위치갱신되면_부르미에게_DELIVERY_LOCATION_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        deliveryService.updateDreamiLocation(orderId, location("37.5", "127.0"));

        verify(sseService).send(eq(boormiId), eq(DeliveryEventType.DELIVERY_LOCATION), any());
    }

    @Test
    void 픽업완료되면_부르미에게_DELIVERY_DELIVERING_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        pickupFinish(orderId);

        verify(sseService).send(eq(boormiId), eq(DeliveryEventType.DELIVERY_DELIVERING), any());
    }

    @Test
    void 배달완료되면_부르미에게_DELIVERY_COMPLETED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DELIVERING, dreamiId, boormiId);

        finish(orderId);

        verify(sseService).send(eq(boormiId), eq(DeliveryEventType.DELIVERY_COMPLETED), any());
    }

    @Test
    void 드리미취소되면_부르미에게_DELIVERY_CANCELLED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        deliveryService.cancelByDreami(orderId);

        verify(sseService).send(eq(boormiId), eq(DeliveryEventType.DELIVERY_CANCELLED), any());
    }

    @Test
    void 부르미취소되면_드리미에게_DELIVERY_CANCELLED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        deliveryService.cancelByBoormi(orderId);

        verify(sseService).send(eq(dreamiId), eq(DeliveryEventType.DELIVERY_CANCELLED), any());
    }

    @Test
    void 관리자취소되면_부르미와_드리미에게_DELIVERY_CANCELLED_SSE전송() {
        UUID dreamiId = UUID.randomUUID();
        UUID boormiId = UUID.randomUUID();
        UUID orderId = registerDeliveryWith(DeliveryCd.PICKUP_NORMAL, dreamiId, boormiId);

        deliveryService.cancelByAdmin(orderId);

        verify(sseService).send(eq(boormiId), eq(DeliveryEventType.DELIVERY_CANCELLED), any());
        verify(sseService).send(eq(dreamiId), eq(DeliveryEventType.DELIVERY_CANCELLED), any());
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

        DeliveryStatusResponseDto result = deliveryService.cancelByDreami(orderId);

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

    // ===== 사진 인증 =====

    @Test
    void 픽업완료_사진이_업로드안됐으면_PICKUP_PHOTO_MISSING_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        given(s3PresignService.isFileUploaded(PHOTO_KEY)).willReturn(false);

        Throwable thrown = catchThrowable(() -> pickupFinish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.PICKUP_PHOTO_MISSING);
        assertThat(statusOf(orderId)).isEqualTo(DeliveryCd.PICKUP_NORMAL);
    }

    @Test
    void 배달완료_사진이_업로드안됐으면_DELIVERY_COMPLETION_PHOTO_MISSING_예외() {
        UUID orderId = registerDelivery(DELIVERING);
        given(s3PresignService.isFileUploaded(PHOTO_KEY)).willReturn(false);

        Throwable thrown = catchThrowable(() -> finish(orderId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DeliveryErrorCode.DELIVERY_COMPLETION_PHOTO_MISSING);
        assertThat(statusOf(orderId)).isEqualTo(DELIVERING);
    }

    @Test
    void 픽업완료_남의_key를_제출하면_KEY_OWNER_MISMATCH_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);
        UUID dreamiId = registeredDeliveries.get(orderId).getDreamiId();
        willThrow(new BusinessException(UploadErrorCode.KEY_OWNER_MISMATCH))
                .given(uploadSessionService)
                .validateScope(UploadPurpose.PICKUP_CERTIFICATION_IMAGE, dreamiId, orderId, PHOTO_KEY);

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
    void 배달완료_픽업전이면_PICKUP_NOT_COMPLETED_예외() {
        UUID orderId = registerDelivery(DeliveryCd.PICKUP_NORMAL);

        Throwable thrown = catchThrowable(() -> finish(orderId));

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
        given(s3PresignService.isFileUploaded(PHOTO_KEY)).willReturn(false);

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
        given(s3PresignService.isFileUploaded(PHOTO_KEY)).willReturn(false);

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
