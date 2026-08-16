package com.naengsam.quick.domain.delivery.dto;

import com.naengsam.quick.domain.boormi.entity.ItemCd;
import java.time.YearMonth;

/**
 * 부르미의 완료 배달을 (월, 물건 유형) 단위로 묶은 집계. 절감액 계산(시장 환산 금액 − 실제 결제액)의 재료다.
 * 시장 환산 금액이 물건 유형 배율을 타서 한 달이 유형 수만큼 여러 행으로 나뉜다.
 * {@code overDistance}는 시장 퀵 기본 구간을 넘긴 거리(m)의 합이다.
 */
public record MonthlySavingAggregate(int year, int month, ItemCd itemCd, long count, long overDistance,
                                     long paidAmount) {

    public YearMonth yearMonth() {
        return YearMonth.of(year, month);
    }
}
