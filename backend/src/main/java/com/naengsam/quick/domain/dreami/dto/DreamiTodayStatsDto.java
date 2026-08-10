package com.naengsam.quick.domain.dreami.dto;

/**
 * 홈 화면에 보여줄 오늘 하루 스코프의 수익·완료 건수.
 */
public record DreamiTodayStatsDto(
        long todayRevenue,
        long todayCompletedCount
) {
    public static DreamiTodayStatsDto of(long todayRevenue, long todayCompletedCount) {
        return new DreamiTodayStatsDto(todayRevenue, todayCompletedCount);
    }
}
