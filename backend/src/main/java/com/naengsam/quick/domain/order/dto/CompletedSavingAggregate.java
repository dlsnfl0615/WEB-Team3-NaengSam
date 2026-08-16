package com.naengsam.quick.domain.order.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;

/**
 * 부르미의 완료 주문을 물건 유형별로 묶은 누적 집계. 시장 환산 금액이 물건 유형 배율을 타므로 유형별로 나눠서 받는다.
 * {@code overDistance}는 시장 퀵 기본 구간을 넘긴 거리(m)의 합이다.
 */
public record CompletedSavingAggregate(ItemCd itemCd, long count, long overDistance, long paidAmount) {
}
