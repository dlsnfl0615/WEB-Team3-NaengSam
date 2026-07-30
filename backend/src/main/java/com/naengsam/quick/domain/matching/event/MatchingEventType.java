package com.naengsam.quick.domain.matching.event;

import com.naengsam.quick.global.sse.SseEventType;

/**
 * SSE로 클라이언트에 뿌리는 매칭 이벤트 종류. {@link #eventName()}이 SSE event 이름으로 사용된다.
 */
public enum MatchingEventType implements SseEventType {
    /**
     * 드리미: 새 제안 팝업 띄우기
     */
    OFFER_POPUP,
    /**
     * 드리미: 제안 팝업 닫기(선착순 마감/거절 완료 등)
     */
    OFFER_CLOSED,
    /**
     * 부르미: 드리미가 수락하여 드리미 정보 전달
     */
    DREAMI_INFO,
    /**
     * 드리미: 부르미가 거절함
     */
    BOORMI_REJECTED,
    /**
     * 대상: 요청 처리 실패 사유(존재하지 않는 제안 등)
     */
    OFFER_ERROR;

    public String eventName() {
        return name().toLowerCase();
    }
}
