package com.naengsam.quick.global.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 브라우저 웹푸시 구독 1건. (브라우저, 기기, 서비스워커 등록) 조합마다 endpoint가 유일하다는 명세를 그대로 이용해
 * <b>endpoint 자체를 기기 식별자로 쓴다</b> — 별도 DEVICE 테이블도, 클라이언트가 만든 device UUID도 두지 않는다.
 *
 * <p>unique 키가 {@code (boormi_id, endpoint)}가 아니라 {@code endpoint} 단독인 것이 핵심이다. 공용 기기에서
 * 같은 endpoint가 다른 계정으로 넘어갈 수 있으므로, 두 번째 행을 만드는 대신 {@link #refresh}로 소유자를
 * <b>재배정</b>해야 한다. 그러지 않으면 이전 사용자가 새 사용자의 알림을 계속 받는다.
 */
@Entity
@Table(name = "PUSH_SUBSCRIPTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    /** 연속 실패가 이 횟수에 이르면 죽은 구독으로 보고 정리한다. */
    public static final int MAX_CONSECUTIVE_FAILURES = 10;

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "push_subscription_id", columnDefinition = "BINARY(16)")
    private UUID pushSubscriptionId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "boormi_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID boormiId;

    @Column(name = "endpoint", length = 512, nullable = false, unique = true)
    private String endpoint;

    @Column(name = "p256dh", length = 255, nullable = false)
    private String p256dh;

    @Column(name = "auth", length = 255, nullable = false)
    private String auth;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_dtm", nullable = false)
    private LocalDateTime createdDtm;

    @Column(name = "last_success_dtm")
    private LocalDateTime lastSuccessDtm;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    public static PushSubscription create(
            UUID boormiId, String endpoint, String p256dh, String auth, String userAgent) {
        PushSubscription subscription = new PushSubscription();
        subscription.pushSubscriptionId = UUID.randomUUID();
        subscription.boormiId = boormiId;
        subscription.endpoint = endpoint;
        subscription.p256dh = p256dh;
        subscription.auth = auth;
        subscription.userAgent = truncateUserAgent(userAgent);
        subscription.createdDtm = LocalDateTime.now();
        subscription.consecutiveFailures = 0;
        return subscription;
    }

    /**
     * 같은 endpoint로 다시 구독 요청이 들어왔을 때 소유자와 키를 현재 값으로 맞춘다. 브라우저가 키를 회전시켰거나
     * 공용 기기의 사용자가 바뀐 경우가 여기로 들어오므로, 실패 카운터도 함께 리셋해 살아난 구독을 정리 대상에서 뺀다.
     */
    public void refresh(UUID boormiId, String p256dh, String auth, String userAgent) {
        this.boormiId = boormiId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = truncateUserAgent(userAgent);
        this.consecutiveFailures = 0;
    }

    public void markSuccess() {
        this.lastSuccessDtm = LocalDateTime.now();
        this.consecutiveFailures = 0;
    }

    public void markFailure() {
        this.consecutiveFailures++;
    }

    public boolean isDead() {
        return consecutiveFailures >= MAX_CONSECUTIVE_FAILURES;
    }

    public boolean isOwnedBy(UUID boormiId) {
        return this.boormiId.equals(boormiId);
    }

    /** User-Agent 헤더는 길이 제한이 없어 컬럼(255)을 넘길 수 있다. 디버깅 참고용이라 잘라 저장한다. */
    private static String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255);
    }
}
