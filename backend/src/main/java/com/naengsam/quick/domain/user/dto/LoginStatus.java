package com.naengsam.quick.domain.user.dto;

/**
 * 로그인 응답 상태.
 *
 * <ul>
 *   <li>SUCCESS : 세션이 만들어졌다. 클라이언트는 바로 다음 화면으로 진행한다</li>
 *   <li>QUEUED  : 대기열에 등록됐다. 발급된 ticketId 로 폴링한다</li>
 *   <li>WAITING : 폴링 응답. 아직 순번이 남았다</li>
 * </ul>
 */
public enum LoginStatus {
    SUCCESS,
    QUEUED,
    WAITING
}
