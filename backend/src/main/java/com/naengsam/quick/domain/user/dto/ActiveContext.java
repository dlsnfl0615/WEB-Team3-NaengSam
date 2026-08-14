package com.naengsam.quick.domain.user.dto;

import com.naengsam.quick.domain.order.entity.OrderCd;
import java.util.UUID;

/**
 * 사용자가 지금 어느 역할로 무엇을 수행 중인지를 한 덩어리로 나타낸다.
 *
 * <p>role이 null이면 아무것도 수행하고 있지 않다는 뜻이고, 이때만 부르미/드리미 전환이 허용된다. orderId는 드리미가 매칭 대기 중(온라인 등록만 한 상태)일 때 null이다 — 그
 * 상태는 주문이 아직 없어 DB에 흔적이 남지 않기 때문이다.
 */
public record ActiveContext(ActiveRole role, UUID orderId, OrderCd orderCd) {

    private static final ActiveContext IDLE = new ActiveContext(null, null, null);

    public static ActiveContext idle() {
        return IDLE;
    }

    public static ActiveContext of(ActiveRole role, UUID orderId, OrderCd orderCd) {
        return new ActiveContext(role, orderId, orderCd);
    }

    /**
     * 드리미가 오퍼를 기다리는 중. 주문이 없으므로 orderId/orderCd는 비어 있다.
     */
    public static ActiveContext dreamiWaiting() {
        return new ActiveContext(ActiveRole.DREAMI, null, null);
    }

    public boolean isActive() {
        return role != null;
    }
}
