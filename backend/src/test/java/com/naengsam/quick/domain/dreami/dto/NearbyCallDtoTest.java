package com.naengsam.quick.domain.dreami.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.address.dto.Addresses;
import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.model.WaitingOrder;
import com.naengsam.quick.domain.order.entity.OrderCd;
import com.naengsam.quick.domain.order.entity.Orders;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NearbyCallDtoTest {

    @Test
    void 주변_콜_정보에_품목_주소_예상수익_ETA와_픽업까지_걸리는_시간이_모두_담긴다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = new GeoPoint(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        Orders order = Orders.create(orderId, UUID.randomUUID(), "서류봉투", ItemCd.DOCUMENT,
                "봉투 A4 사이즈", 5_000L, 12, null, "문 앞에 놔주세요", null,
                Addresses.builder()
                        .originAddressLine1("서울 성북구 동소문로 1")
                        .originAddressLine2("농협 삼선교지점")
                        .destinationAddressLine1("서울 종로구 대학로 136")
                        .destinationAddressLine2("2층")
                        .build(), null);

        // 도보 약 4km/h(66.67m/min) 가정 → 900m는 13.5분, ceil로 14분.
        double distanceMeters = 900.0;
        NearbyOrderDto nearby = NearbyOrderDto.from(new WaitingOrder(orderId, location), distanceMeters);

        // when
        NearbyCallDto dto = NearbyCallDto.from(nearby, order);

        // then
        assertThat(dto.itemName()).isEqualTo("서류봉투");
        assertThat(dto.itemCd()).isEqualTo(ItemCd.DOCUMENT);
        assertThat(dto.orderCd()).isEqualTo(OrderCd.MATCHING);
        assertThat(dto.expectedRevenue()).isEqualTo(5_000L);
        assertThat(dto.expectedEtaMinutes()).isEqualTo(12);
        assertThat(dto.distanceMeters()).isEqualTo(distanceMeters);
        assertThat(dto.pickupEtaMinutes()).isEqualTo(14);
        assertThat(dto.originAddressLine1()).isEqualTo("서울 성북구 동소문로 1");
        assertThat(dto.originAddressLine2()).isEqualTo("농협 삼선교지점");
        assertThat(dto.destinationAddressLine1()).isEqualTo("서울 종로구 대학로 136");
        assertThat(dto.destinationAddressLine2()).isEqualTo("2층");
    }

    @Test
    void 다른_드리미가_이미_수락해_부르미_확인을_기다리는_주문은_orderCd로_구분된다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = new GeoPoint(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        Orders order = Orders.create(orderId, UUID.randomUUID(), "서류봉투", ItemCd.DOCUMENT,
                "봉투 A4 사이즈", 5_000L, 12, null, "문 앞에 놔주세요", null,
                Addresses.builder().build(), null);
        order.markPendingBoormiConfirmation(UUID.randomUUID(), UUID.randomUUID());

        NearbyOrderDto nearby = NearbyOrderDto.from(new WaitingOrder(orderId, location), 500.0);

        // when
        NearbyCallDto dto = NearbyCallDto.from(nearby, order);

        // then
        assertThat(dto.orderCd()).isEqualTo(OrderCd.PENDING_BOORMI_CONFIRMATION);
    }
}
