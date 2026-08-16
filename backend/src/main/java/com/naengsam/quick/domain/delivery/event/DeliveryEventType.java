package com.naengsam.quick.domain.delivery.event;

import com.naengsam.quick.global.sse.SseEventType;

/**
 * SSE로 클라이언트에 뿌리는 배달 이벤트 종류. {@link #eventName()}이 SSE event 이름으로 사용된다.
 * 취소 주체(부르미/드리미/관리자)는 payload(DeliveryStatusResponseDto)의 status로 구분한다.
 */
public enum DeliveryEventType implements SseEventType {
    /**
     * 부르미: 드리미 위치 갱신
     */
    DELIVERY_LOCATION,
    /**
     * 부르미: 픽업 완료되어 배달중 상태로 전환
     */
    DELIVERY_DELIVERING,
    /**
     * 부르미/드리미: 배달이 취소됨(주체는 status로 구분)
     */
    DELIVERY_CANCELLED,
    /**
     * 부르미: 배달 완료
     */
    DELIVERY_COMPLETED,
    /**
     * 부르미: 매칭이 확정돼 배달이 시작됨 → 배달 추적 화면으로 이동
     */
    DELIVERY_STARTED_BOORMI,
    /**
     * 드리미: 매칭이 확정돼 배달이 시작됨 → 배달 추적 화면으로 이동
     */
    DELIVERY_STARTED_DREAMI,
    /**
     * 부르미: 드리미 위치가 일정 시간 이상 들어오지 않음(GPS 권한 차단·브라우저 종료·네트워크 단절).
     * payload는 DreamiOfflineDto. 복구는 별도 이벤트 없이 {@link #DELIVERY_LOCATION} 재개로 알린다.
     */
    DELIVERY_DREAMI_OFFLINE,
    /**
     * 드리미 본인: 내 위치 전송이 끊겼음. {@link #DELIVERY_DREAMI_OFFLINE}과 같은 판정·같은 payload지만
     * <b>이름이 따로 있는 이유는 채널이 다르기 때문</b>이다. 알림 채널 결정표가 이벤트 이름을 키로 쓰므로
     * 한 이름에 수신자별로 다른 채널을 걸 수 없다. 부르미는 화면을 보고 있어 인앱으로 충분하지만, 드리미는
     * 정의상 앱이 죽었거나 백그라운드라 웹푸시로 깨워야 한다.
     */
    DELIVERY_DREAMI_OFFLINE_SELF,
    /**
     * 드리미: 부르미가 연락 시트에서 '핑 보내기'를 눌러 깨웠다. 상태 전이는 없고 "지금 확인해 달라"는 신호만
     * 전달한다. 핑을 보내는 이유 자체가 드리미가 응답이 없어서이므로 인앱만으로는 닿지 않는다 → 웹푸시까지 태운다.
     */
    DELIVERY_PING;

    @Override
    public String eventName() {
        return name().toLowerCase();
    }
}
