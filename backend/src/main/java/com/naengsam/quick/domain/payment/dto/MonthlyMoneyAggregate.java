package com.naengsam.quick.domain.payment.dto;

import java.time.YearMonth;

public record MonthlyMoneyAggregate(int year, int month, long totalAmount, long count) {

    public YearMonth yearMonth() {
        return YearMonth.of(year, month);
    }
}
