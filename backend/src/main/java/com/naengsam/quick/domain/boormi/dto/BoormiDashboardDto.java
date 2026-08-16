package com.naengsam.quick.domain.boormi.dto;

import java.util.List;

/**
 * 부르미 홈·절감 리포트 화면의 이용 통계. 절감액은 같은 배달을 시장 퀵서비스에 맡겼을 때의 환산 금액과 실제 결제액의 차이다.
 * 누적값(completedCount, totalSavedAmount)은 주문 완료 상태 기준이고, 월별값은 배달 완료 시각 기준이다.
 * 절감 리포트가 "시장 환산액 − 실제 결제액" 산술식을 그대로 보여주므로 이번 달 시장 환산액(thisMonthMarketAmount)과
 * 이번 달 실제 결제액(thisMonthPaidAmount)도 함께 내린다.
 */
public record BoormiDashboardDto(
        long completedCount,
        long totalSavedAmount,
        long thisMonthCount,
        long thisMonthPaidAmount,
        long thisMonthSavedAmount,
        long thisMonthMarketAmount,
        long monthOverMonthGrowthPercent,
        List<MonthlySavingDto> recentSixMonths
) {

    public static BoormiDashboardDto of(long completedCount, long totalSavedAmount, long thisMonthCount,
            long thisMonthPaidAmount, long thisMonthSavedAmount, long thisMonthMarketAmount,
            long monthOverMonthGrowthPercent, List<MonthlySavingDto> recentSixMonths) {
        return new BoormiDashboardDto(completedCount, totalSavedAmount, thisMonthCount, thisMonthPaidAmount,
                thisMonthSavedAmount, thisMonthMarketAmount, monthOverMonthGrowthPercent, recentSixMonths);
    }
}
