package com.naengsam.quick.domain.matching.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import com.naengsam.quick.domain.matching.dto.GeoPoint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * OrderOfferGroup이 최초 매칭 시작 시각(matchingStartedAt)을 생성 시점 그대로 저장하고, 재매칭으로 상태가
 * 전이돼도(closeForRematch → addOffersAndOpen) 그 값이 초기화되지 않는지 확인한다.
 */
class OrderOfferGroupTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID BOORMI_ID = UUID.randomUUID();
    private static final LocalDateTime MATCHING_STARTED_AT = LocalDateTime.of(2026, 8, 9, 9, 0);

    @Test
    void 생성_시_매칭_시작_시각이_저장된다() {
        OrderOfferGroup group = newGroup();

        assertThat(group.matchingStartedAt()).isEqualTo(MATCHING_STARTED_AT);
    }

    @Test
    void 재매칭_전이_후에도_최초_매칭_시작_시각이_유지된다() {
        OrderOfferGroup group = newGroup();

        group.closeForRematch();
        group.addOffersAndOpen(List.of());

        assertThat(group.matchingStartedAt()).isEqualTo(MATCHING_STARTED_AT);
    }

    @Test
    void 매칭_시작_시각이_null이면_예외가_발생한다() {
        Throwable thrown = catchThrowable(
                () -> new OrderOfferGroup(ORDER_ID, BOORMI_ID, mock(GeoPoint.class), List.of(), null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private OrderOfferGroup newGroup() {
        return new OrderOfferGroup(ORDER_ID, BOORMI_ID, mock(GeoPoint.class), List.of(), MATCHING_STARTED_AT);
    }
}
