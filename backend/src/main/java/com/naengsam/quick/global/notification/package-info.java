/**
 * 도메인에 독립적인 알림 채널 선택과 전송 진입점을 제공한다.
 *
 * <p>알림은 매칭·배달 등 여러 도메인에서 함께 사용하는 횡단 인프라다. 이를 별도 도메인에 두면 도메인 간
 * 역방향 의존이 생기므로, 기존 {@code global.sse}와 같은 공통 계층에 둔다.
 */
package com.naengsam.quick.global.notification;
