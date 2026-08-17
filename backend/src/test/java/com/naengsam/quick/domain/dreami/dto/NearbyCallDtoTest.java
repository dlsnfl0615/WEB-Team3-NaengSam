package com.naengsam.quick.domain.dreami.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import com.naengsam.quick.domain.matching.dto.GeoPoint;
import com.naengsam.quick.domain.matching.dto.NearbyOrderDto;
import com.naengsam.quick.domain.matching.model.WaitingOrder;
import com.naengsam.quick.domain.order.dto.NearbyCallOrderDto;
import com.naengsam.quick.domain.order.entity.OrderCd;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NearbyCallDtoTest {

    @Test
    void 주변_콜_정보에_품목_주소_예상수익_ETA와_픽업까지_걸리는_시간이_모두_담긴다() {
        // given
        UUID orderId = UUID.randomUUID();
        GeoPoint location = new GeoPoint(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        NearbyCallOrderDto order = new NearbyCallOrderDto(orderId, "서류봉투", ItemCd.DOCUMENT,
                OrderCd.MATCHING, 5_000L, 12,
                "서울 성북구 동소문로 1", "농협 삼선교지점",
                "서울 종로구 대학로 136", "2층");

        // 도보 약 4km/h(66.67m/min) 가정 → 900m는 13.5분, ceil로 14분.
        double distanceMeters = 900.0;
        NearbyOrderDto nearby = NearbyOrderDto.from(new WaitingOrder(orderId, location), distanceMeters);

        // when
        NearbyCallDto dto = NearbyCallDto.from(nearby, order);

        // then
        assertThat(dto.orderId()).isEqualTo(orderId);
        assertThat(dto.location()).isEqualTo(location);
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
}
