package com.naengsam.quick.domain.dreami.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.boormi.repository.BoormiRepository;
import com.naengsam.quick.domain.dreami.dto.DreamiProfileDto;
import com.naengsam.quick.domain.dreami.dto.NearbyCallDto;
import com.naengsam.quick.domain.dreami.entity.Dreami;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.dreami.exception.DreamiErrorCode;
import com.naengsam.quick.domain.dreami.repository.DreamiRepository;
import com.naengsam.quick.domain.dreami.repository.DreamiRequestDeniedDetailsRepository;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.dto.NearbyOrderRequest;
import com.naengsam.quick.domain.matching.service.NearbyOrderFinder;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.repository.OrderRepository;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 드리미 서비스 단위 테스트. 프로필 조회 시 이름/평점/거절횟수를, 주변 콜 조회 시 위치/거리와 주문 상세를 올바르게 조합하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class DreamiServiceTest {

    @Mock
    private DreamiRepository dreamiRepository;

    @Mock
    private BoormiRepository boormiRepository;

    @Mock
    private DreamiRequestDeniedDetailsRepository dreamiRequestDeniedDetailsRepository;

    @Mock
    private NearbyOrderFinder nearbyOrderFinder;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private DreamiService dreamiService;

    private static BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }

    private static Boormi activeBoormi() {
        return Boormi.create("dreami@test.com", "pass123", "김드림", "01098765432",
                LocalDate.of(1995, 5, 5));
    }

    // ---------- getDreamiProfile ----------

    @Test
    void 프로필조회_정상이면_이름_평점_거절횟수를_담아_반환한다() {
        Boormi boormi = activeBoormi();
        UUID id = boormi.getBoormiId();
        Dreami dreami = Dreami.create(id, "idCardKey", "criminalRecordKey");
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));
        given(boormiRepository.findById(id)).willReturn(Optional.of(boormi));
        given(dreamiRequestDeniedDetailsRepository.countByDreamiId(id)).willReturn(2L);

        DreamiProfileDto result = dreamiService.getDreamiProfile(id);

        assertThat(result.name()).isEqualTo("김드림");
        assertThat(result.dreamiAvgScore()).isEqualByComparingTo(dreami.getDreamiAvgScore());
        assertThat(result.rejectCount()).isEqualTo(2L);
    }

    @Test
    void 프로필조회_드리미가_없으면_NOT_FOUND_예외() {
        UUID id = UUID.randomUUID();
        given(dreamiRepository.findById(id)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> dreamiService.getDreamiProfile(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(DreamiErrorCode.NOT_FOUND);
    }

    @Test
    void 프로필조회_부르미가_없으면_NOT_FOUND_예외() {
        UUID id = UUID.randomUUID();
        Dreami dreami = Dreami.create(id, "idCardKey", "criminalRecordKey");
        given(dreamiRepository.findById(id)).willReturn(Optional.of(dreami));
        given(boormiRepository.findById(id)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> dreamiService.getDreamiProfile(id));

        assertThat(errorCodeOf(thrown)).isEqualTo(DreamiErrorCode.NOT_FOUND);
    }

    // ---------- assertNotAlreadyApproved ----------

    @Test
    void 승인된_드리미면_ALREADY_APPROVED_예외() {
        UUID dreamiId = UUID.randomUUID();
        Dreami dreami = Dreami.create(dreamiId, "idCardKey", "criminalRecordKey");
        ReflectionTestUtils.setField(dreami, "requestCd", DreamiCd.APPROVED);
        given(dreamiRepository.findById(dreamiId)).willReturn(Optional.of(dreami));

        Throwable thrown = catchThrowable(() -> dreamiService.assertNotAlreadyApproved(dreamiId));

        assertThat(errorCodeOf(thrown)).isEqualTo(DreamiErrorCode.ALREADY_APPROVED);
    }

    @Test
    void 승인되지_않은_드리미면_예외없이_통과한다() {
        UUID dreamiId = UUID.randomUUID();
        Dreami dreami = Dreami.create(dreamiId, "idCardKey", "criminalRecordKey"); // 기본 상태 REQUESTED
        given(dreamiRepository.findById(dreamiId)).willReturn(Optional.of(dreami));

        assertThatCode(() -> dreamiService.assertNotAlreadyApproved(dreamiId)).doesNotThrowAnyException();
    }

    @Test
    void 신청기록이_없으면_예외없이_통과한다() {
        UUID dreamiId = UUID.randomUUID();
        given(dreamiRepository.findById(dreamiId)).willReturn(Optional.empty());

        assertThatCode(() -> dreamiService.assertNotAlreadyApproved(dreamiId)).doesNotThrowAnyException();
    }

    // ---------- findNearbyCalls ----------

    @Test
    void 주변콜조회_정상이면_거리와_주문상세를_조합해_반환한다() {
        NearbyOrderRequest request = new NearbyOrderRequest(
                new BigDecimal("37.5"), new BigDecimal("127.0"), 1000.0, 10);
        UUID orderId = UUID.randomUUID();
        GeoPoint location = new GeoPoint(new BigDecimal("37.501"), new BigDecimal("127.001"));
        NearbyOrderDto nearbyOrder = new NearbyOrderDto(orderId, location, 120.5);
        given(nearbyOrderFinder.find(request)).willReturn(List.of(nearbyOrder));

        UUID boormiId = UUID.randomUUID();
        Orders order = Orders.create(orderId, boormiId, location, location);
        ReflectionTestUtils.setField(order, "itemName", "서류봉투");
        ReflectionTestUtils.setField(order, "deliveryAmount", 3500L);
        ReflectionTestUtils.setField(order, "deliveryEta", 15);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        List<NearbyCallDto> result = dreamiService.findNearbyCalls(request);

        assertThat(result).hasSize(1);
        NearbyCallDto dto = result.getFirst();
        assertThat(dto.orderId()).isEqualTo(orderId);
        assertThat(dto.distanceMeters()).isEqualTo(120.5);
        assertThat(dto.itemName()).isEqualTo("서류봉투");
        assertThat(dto.expectedRevenue()).isEqualTo(3500L);
        assertThat(dto.expectedEtaMinutes()).isEqualTo(15);
    }

    @Test
    void 주변콜조회_주문을_찾을_수_없으면_ORDER_NOT_FOUND_예외() {
        NearbyOrderRequest request = new NearbyOrderRequest(
                new BigDecimal("37.5"), new BigDecimal("127.0"), 1000.0, 10);
        UUID orderId = UUID.randomUUID();
        NearbyOrderDto nearbyOrder = new NearbyOrderDto(orderId,
                new GeoPoint(new BigDecimal("37.501"), new BigDecimal("127.001")), 120.5);
        given(nearbyOrderFinder.find(request)).willReturn(List.of(nearbyOrder));
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> dreamiService.findNearbyCalls(request));

        assertThat(errorCodeOf(thrown)).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }
}
