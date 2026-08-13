package com.naengsam.quick.domain.order.dto;

public record OrderCountDto(long count) {

    public static OrderCountDto of(long count) {
        return new OrderCountDto(count);
    }
}
