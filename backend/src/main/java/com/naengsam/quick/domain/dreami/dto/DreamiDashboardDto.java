package com.naengsam.quick.domain.dreami.dto;

import java.util.List;

public record DreamiDashboardDto(
        long completedCount,
        long thisMonthRevenue,
        long monthOverMonthGrowthPercent,
        long thisMonthCount,
        List<MonthlyRevenueDto> recentSixMonths
) {

    public static DreamiDashboardDto of(long completedCount, long thisMonthRevenue, long monthOverMonthGrowthPercent,
                                        long thisMonthCount, List<MonthlyRevenueDto> recentSixMonths) {
        return new DreamiDashboardDto(completedCount, thisMonthRevenue, monthOverMonthGrowthPercent, thisMonthCount,
                recentSixMonths);
    }
}
