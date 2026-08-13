package com.naengsam.quick.domain.boormi.dto;

import java.util.List;

/**
 * 부르미 홈·절감 리포트 화면의 이용 통계. 절감액은 시장 퀵서비스 평균 단가와 실제 결제액의 차이다.
 * 누적값(completedCount, totalSavedAmount)은 주문 완료 상태 기준이고, 월별값은 배달 완료 시각 기준이다.
 * 절감 리포트가 "N건 × 단가 − 실제 결제액" 산술식을 그대로 보여주므로 기준 단가(marketUnitPrice)와
 * 이번 달 실제 결제액(thisMonthPaidAmount)도 함께 내린다.
 */
public record BoormiDashboardDto(
        long completedCount,
        long totalSavedAmount,
        long thisMonthCount,
        long thisMonthPaidAmount,
        long thisMonthSavedAmount,
        long marketUnitPrice,
        long monthOverMonthGrowthPercent,
        List<MonthlySavingDto> recentSixMonths
) {

    public static BoormiDashboardDto of(long completedCount, long totalSavedAmount, long thisMonthCount,
            long thisMonthPaidAmount, long thisMonthSavedAmount, long marketUnitPrice,
            long monthOverMonthGrowthPercent, List<MonthlySavingDto> recentSixMonths) {
        return new BoormiDashboardDto(completedCount, totalSavedAmount, thisMonthCount, thisMonthPaidAmount,
                thisMonthSavedAmount, marketUnitPrice, monthOverMonthGrowthPercent, recentSixMonths);
    }
}
