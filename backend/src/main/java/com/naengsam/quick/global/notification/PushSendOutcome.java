package com.naengsam.quick.global.notification;

/**
 * 웹푸시 한 건의 전송 결과. 푸시 서비스의 응답 코드마다 구독 행을 어떻게 다뤄야 하는지가 다르기 때문에,
 * HTTP 상태를 그대로 흘리지 않고 처리 방침이 드러나는 값으로 좁혀서 전달한다.
 */
public enum PushSendOutcome {

    /** 2xx. 실패 카운터를 리셋한다. */
    SUCCESS,

    /**
     * 404·410. 구독이 영구 소멸했다(앱 삭제, 사이트 데이터 초기화). 즉시 삭제해야 한다.
     * 가장 중요한 정리 경로다 — 없으면 죽은 행이 쌓여 매 전송이 HTTP 왕복을 낭비한다.
     */
    EXPIRED,

    /** 429. 재시도하면 도착할 때쯤 알림이 이미 낡으므로 그냥 버린다. 행은 건드리지 않는다. */
    RATE_LIMITED,

    /** 413. wake-up 봉투 크기로는 나올 수 없는 값이라 발생하면 우리 버그다. 행은 건드리지 않는다. */
    PAYLOAD_TOO_LARGE,

    /**
     * VAPID 키 불일치 같은 설정 오류(위에 해당하지 않는 4xx). 행을 지우면 키를 잘못 넣은 배포 한 번에
     * 전체 구독이 날아가므로 절대 삭제하지 않는다.
     */
    REJECTED,

    /** 5xx·타임아웃·암호화 실패 등. 연속 실패를 세다가 임계치를 넘으면 정리한다. */
    RETRIABLE_FAILURE;

    public static PushSendOutcome fromStatusCode(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return SUCCESS;
        }
        return switch (statusCode) {
            case 404, 410 -> EXPIRED;
            case 429 -> RATE_LIMITED;
            case 413 -> PAYLOAD_TOO_LARGE;
            default -> statusCode >= 500 ? RETRIABLE_FAILURE : REJECTED;
        };
    }

    /** Micrometer 태그 값. {@code notification.dropped{reason=...}} 에 쓴다. */
    public String metricReason() {
        return name().toLowerCase();
    }
}
