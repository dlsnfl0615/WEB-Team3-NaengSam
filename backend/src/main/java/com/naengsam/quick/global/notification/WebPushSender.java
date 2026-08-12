package com.naengsam.quick.global.notification;

import com.naengsam.quick.global.notification.dto.PushEnvelope;
import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * VAPID 서명과 aes128gcm 페이로드 암호화를 얹어 푸시 서비스로 wake-up 봉투를 보낸다.
 * {@code web-push.enabled=true} 일 때만 빈으로 등록되며, 비활성이면 {@link NotificationService}가 웹푸시 채널을
 * 통째로 건너뛴다(로그만 찍는 dev double은 두지 않는다).
 *
 * <p>여기서는 <b>전송만</b> 하고 구독 행은 건드리지 않는다. 결과에 따른 정리는 {@link PushSubscriptionService}가
 * 맡는다. 이 클래스의 모든 메서드는 {@link NotificationService}의 outbound executor 위에서 호출되므로 블로킹
 * HTTP를 해도 되지만, 호출 스레드(특히 매칭 엔진)에서 직접 부르면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "web-push.enabled", havingValue = "true")
public class WebPushSender {

    /**
     * 이 시간 이하의 TTL을 가진 알림은 즉시성이 생명인 오퍼류로 보고 {@code Urgency: high}로 보낸다.
     * 배달 진행 상태처럼 TTL이 분·시간 단위인 알림은 기본 우선순위로 두어 기기 배터리 정책을 존중한다.
     */
    private static final Duration HIGH_URGENCY_TTL_THRESHOLD = Duration.ofMinutes(1);

    private final WebPushProperties properties;
    private final ObjectMapper objectMapper;

    private PushService pushService;

    @PostConstruct
    void init() {
        Security.addProvider(new BouncyCastleProvider());
        try {
            pushService = new PushService(properties.publicKey(), properties.privateKey(), properties.subject());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 키가 깨진 채로 뜨면 모든 전송이 조용히 실패하므로, 차라리 기동을 실패시켜 배포에서 잡는다.
            // BouncyCastle 은 키 형식이 어긋나면 checked 예외가 아니라 IllegalArgumentException("Invalid point
            // encoding") 을 던지므로 함께 잡아, 원인이 VAPID 설정임을 메시지에 남긴다.
            throw new IllegalStateException("VAPID 키가 올바르지 않아 웹푸시를 초기화할 수 없습니다.", e);
        }
    }

    /**
     * 구독 1건에 봉투를 보낸다. 예외를 밖으로 던지지 않고 {@link PushSendOutcome}으로 환원한다 — 푸시 실패가
     * 도메인 로직이나 인앱 알림에 영향을 주면 안 되기 때문이다.
     *
     * @param eventName SSE 이벤트 이름. 알림 tag로 써서 같은 종류의 연속 알림이 쌓이지 않고 교체되게 한다.
     */
    public PushSendOutcome send(PushSubscription subscription, ChannelPlan plan, String eventName) {
        try {
            Notification notification = Notification.builder()
                    .endpoint(subscription.getEndpoint())
                    .userPublicKey(subscription.getP256dh())
                    .userAuth(subscription.getAuth())
                    .payload(objectMapper.writeValueAsString(envelopeOf(plan, eventName)))
                    .ttl((int) plan.pushTtl().toSeconds())
                    .urgency(urgencyOf(plan))
                    .build();

            HttpResponse response = pushService.send(notification);
            return PushSendOutcome.fromStatusCode(response.getStatusLine().getStatusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushSendOutcome.RETRIABLE_FAILURE;
        } catch (Exception e) {
            log.warn("웹푸시 전송 실패: event={}, reason={}", eventName, e.toString());
            return PushSendOutcome.RETRIABLE_FAILURE;
        }
    }

    private PushEnvelope envelopeOf(ChannelPlan plan, String eventName) {
        // 앱을 열기만 하면 기존 스냅샷 API가 정확한 현재 상태를 가져오므로 딥링크 대신 루트로 보낸다.
        return new PushEnvelope(plan.pushTitle(), plan.pushBody(), "/", eventName);
    }

    /**
     * TTL을 오퍼 TTL과 같게 두는 것이 핵심 안전장치다. TTL이 30이면 푸시 서비스가 제때 전달하지 못한 오퍼 wake-up을
     * <b>폐기</b>한다. 몇 분 뒤 폰이 깨어날 때 배달되어 사용자가 앱을 열었더니 아무것도 없는 최악의 실패 모드를
     * 이 헤더 하나가 막는다.
     */
    private Urgency urgencyOf(ChannelPlan plan) {
        return plan.pushTtl().compareTo(HIGH_URGENCY_TTL_THRESHOLD) <= 0 ? Urgency.HIGH : Urgency.NORMAL;
    }
}
