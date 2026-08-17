package com.naengsam.quick.domain.order.dto;

import com.naengsam.quick.domain.order.entity.OrderCd;

/**
 * 활동 내역 화면의 상태별(전체/진행중/완료/취소) 탭 개수 표시용.
 */
public record OrderStatusCountDto(OrderCd orderCd, long count) {
}
