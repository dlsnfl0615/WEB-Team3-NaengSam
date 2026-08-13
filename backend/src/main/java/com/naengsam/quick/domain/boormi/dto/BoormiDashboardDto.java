package com.naengsam.quick.domain.boormi.dto;

import java.util.List;

/**
 * 부르미 홈·절감 리포트 화면의 이용 통계. 절감액은 시장 퀵서비스 평균 단가와 실제 결제액의 차이다.
 * 누적값(completedCount, totalSavedAmount)은 주문 완료 상태 기준이고, 월별값은 배달 완료 시각 기준이다.
 */
public record BoormiDashboardDto(
        long completedCount,
        long totalSavedAmount,
        long thisMonthCount,
        long thisMonthSavedAmount,
        long monthOverMonthGrowthPercent,
        List<MonthlySavingDto> recentSixMonths
) {

    public static BoormiDashboardDto of(long completedCount, long totalSavedAmount, long thisMonthCount,
            long thisMonthSavedAmount, long monthOverMonthGrowthPercent, List<MonthlySavingDto> recentSixMonths) {
        return new BoormiDashboardDto(completedCount, totalSavedAmount, thisMonthCount, thisMonthSavedAmount,
                monthOverMonthGrowthPercent, recentSixMonths);
    }
}
