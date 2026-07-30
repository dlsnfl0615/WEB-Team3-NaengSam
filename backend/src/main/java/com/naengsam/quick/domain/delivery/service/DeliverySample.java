package com.naengsam.quick.domain.delivery.service;

/**
 * Action + Queue 패턴의 permits 대상 예시(placeholder). 실제 배달 액션이 정의되기 전까지 구조만 보여준다.
 * TODO: 배달 도메인 액션(예: DeliveryStart, DeliveryComplete 등)으로 교체.
 */
record DeliverySample() implements Action {

    @Override
    public void execute() {
        // 아직 코드 구현X
    }
}
