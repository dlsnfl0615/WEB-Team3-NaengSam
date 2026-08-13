package com.naengsam.quick.domain.user.dto;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.order.entity.OrderCd;
import java.util.UUID;

public record UserDto(
        UUID boormiId,
        String email,
        String name,
        boolean isDreami,
        ActiveRole activeRole,
        UUID activeOrderId,
        OrderCd activeOrderCd
) {
    public static UserDto from(Boormi boormi, boolean isDreami) {
        return from(boormi, isDreami, ActiveContext.idle());
    }

    public static UserDto from(Boormi boormi, boolean isDreami, ActiveContext activeContext) {
        return new UserDto(boormi.getBoormiId(), boormi.getEmail(), boormi.getName(), isDreami,
                activeContext.role(), activeContext.orderId(), activeContext.orderCd());
    }
}
