package com.naengsam.quick.domain.user.dto;

import com.naengsam.quick.domain.boormi.entity.Boormi;
import com.naengsam.quick.domain.dreami.entity.DreamiCd;
import com.naengsam.quick.domain.order.entity.OrderCd;
import java.math.BigDecimal;
import java.util.UUID;

public record UserDto(
        UUID boormiId,
        String email,
        String name,
        boolean isDreami,
        boolean isAdmin,
        BigDecimal boormiAvgScore,
        BigDecimal dreamiAvgScore,
        // 드리미 신청 이력의 실제 상태. 신청한 적이 없으면 null(=미신청), 있으면 REQUESTED/REVIEWING/APPROVED/REJECTED.
        DreamiCd dreamiStatus,
        ActiveRole activeRole,
        UUID activeOrderId,
        OrderCd activeOrderCd
) {
    public static UserDto from(Boormi boormi, boolean isDreami) {
        return from(boormi, isDreami, null, null, ActiveContext.idle());
    }

    public static UserDto from(Boormi boormi, boolean isDreami, BigDecimal dreamiAvgScore,
            DreamiCd dreamiStatus, ActiveContext activeContext) {
        return new UserDto(boormi.getBoormiId(), boormi.getEmail(), boormi.getName(), isDreami, boormi.isAdmin(),
                boormi.getBoormiAvgScore(), dreamiAvgScore, dreamiStatus,
                activeContext.role(), activeContext.orderId(), activeContext.orderCd());
    }
}
