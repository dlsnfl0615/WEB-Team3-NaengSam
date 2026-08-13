package com.naengsam.quick.domain.boormi.dto;

/**
 * 부르미 홈 화면의 누적 이용 통계. 절감액은 시장 퀵서비스 평균 단가와 실제 결제액의 차이다.
 */
public record BoormiDashboardDto(
        long completedCount,
        long totalSavedAmount,
        long thisMonthCount
) {

    public static BoormiDashboardDto of(long completedCount, long totalSavedAmount, long thisMonthCount) {
        return new BoormiDashboardDto(completedCount, totalSavedAmount, thisMonthCount);
    }
}
