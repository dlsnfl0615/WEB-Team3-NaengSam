package com.naengsam.quick.domain.dreami.dto;

import java.util.List;

/**
 * 커서 기반 드리미 활동 내역 조회 응답. {@code nextCursor} 는 다음 페이지 조회에 그대로 넘기며, 다음 페이지가 없으면 {@code hasNext=false} 이고 null 이다.
 */
public record DreamiDeliveryHistoryResponse(
        List<DreamiDeliveryHistoryDto> deliveries,
        String nextCursor,
        boolean hasNext
) {
    public static DreamiDeliveryHistoryResponse of(
            List<DreamiDeliveryHistoryDto> deliveries, String nextCursor, boolean hasNext) {
        return new DreamiDeliveryHistoryResponse(deliveries, nextCursor, hasNext);
    }
}
