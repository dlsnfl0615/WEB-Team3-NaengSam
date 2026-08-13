package com.naengsam.quick.domain.user.dto;

/**
 * 로그인 / 대기열 폴링 응답. SUCCESS 면 대기열 관련 필드는 모두 {@code null} 이다.
 *
 * <p>{@code pollAfterMs} 는 서버가 정해서 내려주는 다음 폴링까지의 간격이다. 순번이 앞당겨질수록 짧아지므로
 * 클라이언트는 고정 주기 대신 이 값으로 재예약하면 된다. 상·하한을 서버가 쥐고 있어야 부하 상황에서
 * 백오프를 조절할 수 있다.
 */
public record LoginResultDto(
        LoginStatus status,
        String ticketId,
        Integer position,
        Integer totalWaiting,
        Integer estimatedWaitSeconds,
        Integer pollAfterMs
) {

    public static LoginResultDto success() {
        return new LoginResultDto(LoginStatus.SUCCESS, null, null, null, null, null);
    }

    public static LoginResultDto queued(String ticketId, int position, int totalWaiting,
                                        int estimatedWaitSeconds, int pollAfterMs) {
        return new LoginResultDto(LoginStatus.QUEUED, ticketId, position, totalWaiting,
                estimatedWaitSeconds, pollAfterMs);
    }

    public static LoginResultDto waiting(int position, int totalWaiting,
                                         int estimatedWaitSeconds, int pollAfterMs) {
        return new LoginResultDto(LoginStatus.WAITING, null, position, totalWaiting,
                estimatedWaitSeconds, pollAfterMs);
    }
}
