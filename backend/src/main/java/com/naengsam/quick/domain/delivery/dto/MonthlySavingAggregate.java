package com.naengsam.quick.domain.delivery.dto;

import java.time.YearMonth;

/**
 * 부르미의 월별 완료 배달 건수와 실제 결제 금액 합계. 절감액 계산(건수 × 시장 단가 − 결제액)의 재료다.
 */
public record MonthlySavingAggregate(int year, int month, long count, long paidAmount) {

    public YearMonth yearMonth() {
        return YearMonth.of(year, month);
    }
}
