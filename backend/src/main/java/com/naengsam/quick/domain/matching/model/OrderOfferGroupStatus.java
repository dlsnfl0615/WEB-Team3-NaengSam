package com.naengsam.quick.domain.matching.model;

public enum OrderOfferGroupStatus {
    /**
     * micro-batch 다음 라운드를 기다리는 중 (최초 매칭 시작 직후 또는 현재 라운드 오퍼가 모두 종료된 뒤의 재매칭 대기)
     */
    WAITING,
    /**
     * 오퍼가 한 건 이상 나가 드리미/부르미 응답을 기다리는 중
     */
    OPEN,
    /**
     * 드리미+부르미 모두 수락하여 매칭 확정
     */
    MATCHED,
    /**
     * 부르미가 주문을 취소함
     */
    CANCELLED
}
