package com.naengsam.quick.domain.boormi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import com.naengsam.quick.domain.order.exception.OrderErrorCode;
import com.naengsam.quick.domain.order.service.OrderService;
import com.naengsam.quick.domain.payment.service.PaymentService;
import com.naengsam.quick.global.code.GeneralErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private KakaoDirectionsService kakaoDirectionsService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private BoormiService boormiService;

    private static ExpectedValueRequest request(ItemCd itemCd) {
        return new ExpectedValueRequest("서울시 강남구", "서울시 서초구", itemCd);
    }

    private static OrderRequest orderRequest() {
        return new OrderRequest("서울시 강남구", "101동", "서울시 서초구", "202동",
                "서류봉투", ItemCd.DOCUMENT, 5000, 20, "http://img", "계약서", "문 앞에 두세요");
    }

    private static OrderRequest sameLocationOrderRequest() {
        return new OrderRequest("서울시 강남구", "101동", "서울시 강남구", "101동",
                "서류봉투", ItemCd.DOCUMENT, 5000, 20, "http://img", "계약서", "문 앞에 두세요");
    }

    private static CoordinatesResponseDto coordinates() {
        CoordinatesResponseDto.RoadAddress roadAddress =
                new CoordinatesResponseDto.RoadAddress(
                        null, null, null, null, null, null, null, null, null,
                        "127.123456", "37.123456");
        return new CoordinatesResponseDto(List.of(new CoordinatesResponseDto.Document(roadAddress)));
    }

    @Test
    void 문서_5km면_기본요금과_거리요금을_합산한다() {
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willReturn(new KakaoDirectionsResponseDto.Properties(5000, 900));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.DOCUMENT));

        assertThat(result.expectedValue()).isEqualTo(10100);
        assertThat(result.expectedTime()).isEqualTo(15);
        assertThat(result.expectedDistance()).isEqualTo(5000);
    }

    @Test
    void PACKAGE는_배율15이_곱해진다() {
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willReturn(new KakaoDirectionsResponseDto.Properties(5000, 900));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.PACKAGE));

        assertThat(result.expectedValue()).isEqualTo(15150);
    }

    @Test
    void ETA는_초를_분으로_올림한다() {
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willReturn(new KakaoDirectionsResponseDto.Properties(5000, 901));

        ExpectedValueDto result = boormiService.expectedValue(request(ItemCd.DOCUMENT));

        assertThat(result.expectedTime()).isEqualTo(16);
    }

    @Test
    void 견적_좌표변환시_x는_경도_y는_위도로_매핑한다() {
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(kakaoDirectionsService.getRoute(any(), any()))
                .willReturn(new KakaoDirectionsResponseDto.Properties(5000, 900));

        boormiService.expectedValue(request(ItemCd.DOCUMENT));

        ArgumentCaptor<GeoPoint> captor = ArgumentCaptor.forClass(GeoPoint.class);
        then(kakaoDirectionsService).should().getRoute(captor.capture(), captor.capture());
        GeoPoint origin = captor.getAllValues().getFirst();
        assertThat(origin.latitude()).isEqualByComparingTo("37.123456");   // y=위도
        assertThat(origin.longitude()).isEqualByComparingTo("127.123456"); // x=경도
    }

    @Test
    void 주문접수_요청필드로_주문을_생성해_저장하고_결제와_매칭을_시작한다() {
        UUID boormiId = UUID.randomUUID();
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(matchingService.startMatching(any())).willReturn(true);

        boormiService.subscribeOrder(orderRequest(), boormiId);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        then(orderService).should().createOrders(captor.capture());
        then(paymentService).should().startPayment();
        then(matchingService).should().startMatching(captor.getValue());

        Orders saved = captor.getValue();
        assertThat(saved.getBoormiId()).isEqualTo(boormiId);
        assertThat(saved.getItemName()).isEqualTo("서류봉투");
        assertThat(saved.getItemCd()).isEqualTo(ItemCd.DOCUMENT);
        assertThat(saved.getDeliveryAmount()).isEqualTo(5000L);
        assertThat(saved.getDeliveryEta()).isEqualTo(20);
        assertThat(saved.getOrderCd()).isEqualTo(OrderCd.MATCHING);
        assertThat(saved.getOriginAddressLine1()).isEqualTo("서울시 강남구");
        assertThat(saved.getDestinationAddressLine2()).isEqualTo("202동");
        // x=경도(127.x), y=위도(37.x)가 latitude/longitude 자리에 올바르게 매핑되어야 한다
        assertThat(saved.getOriginLatitude()).isEqualByComparingTo("37.123456");
        assertThat(saved.getOriginLongitude()).isEqualByComparingTo("127.123456");
        assertThat(saved.getDestinationLatitude()).isEqualByComparingTo("37.123456");
        assertThat(saved.getDestinationLongitude()).isEqualByComparingTo("127.123456");
    }

    @Test
    void 주문접수_출발지와_도착지가_같으면_SAME_ORIGIN_DESTINATION() {
        UUID boormiId = UUID.randomUUID();

        Throwable thrown = catchThrowable(
                () -> boormiService.subscribeOrder(sameLocationOrderRequest(), boormiId));

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
        given(coordinatesService.getCoordinates(anyString())).willReturn(coordinates());
        given(matchingService.startMatching(any())).willReturn(false);

        Throwable thrown = catchThrowable(() -> boormiService.subscribeOrder(orderRequest(), boormiId));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(GeneralErrorCode.CONFLICT);
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
}
