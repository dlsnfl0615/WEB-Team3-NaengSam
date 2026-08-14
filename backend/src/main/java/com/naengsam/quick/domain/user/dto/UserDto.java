package com.naengsam.quick.domain.user.dto;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.order.entity.OrderCd;
import java.math.BigDecimal;
import java.util.UUID;

public record UserDto(
        UUID boormiId,
        String email,
        String name,
        boolean isDreami,
        BigDecimal boormiAvgScore,
        BigDecimal dreamiAvgScore,
        ActiveRole activeRole,
        UUID activeOrderId,
        OrderCd activeOrderCd
) {
    public static UserDto from(Boormi boormi, boolean isDreami) {
        return from(boormi, isDreami, null, ActiveContext.idle());
    }

    public static UserDto from(Boormi boormi, boolean isDreami, BigDecimal dreamiAvgScore,
            ActiveContext activeContext) {
        return new UserDto(boormi.getBoormiId(), boormi.getEmail(), boormi.getName(), isDreami,
                boormi.getBoormiAvgScore(), dreamiAvgScore,
                activeContext.role(), activeContext.orderId(), activeContext.orderCd());
    }
}
