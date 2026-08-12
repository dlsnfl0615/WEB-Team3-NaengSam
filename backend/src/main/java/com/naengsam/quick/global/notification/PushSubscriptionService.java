package com.naengsam.quick.global.notification;

import com.naengsam.quick.global.notification.dto.PushSubscriptionRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹푸시 구독 행의 생명주기를 담당한다. 전송 자체(HTTP·암호화)는 {@link WebPushSender}가 하고, 그 결과에 따라
 * 행을 어떻게 정리할지는 여기서 결정한다.
 *
 * <p>트랜잭션을 전송 루프 전체가 아니라 <b>조회 1회 + 결과 반영 1회</b>로 잘게 나눈 것이 의도다. 한 사용자가 여러
 * 기기를 등록해 두었을 때 푸시 서비스로의 HTTP 왕복 여러 번을 하나의 트랜잭션이 감싸면 그동안 DB 커넥션을 붙잡게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final MeterRegistry meterRegistry;

    /**
     * 구독을 등록하거나 갱신한다(멱등). 같은 endpoint가 이미 있으면 행을 새로 만들지 않고 소유자와 키를 재배정한다 —
     * 공용 기기에서 계정이 바뀌었을 때 이전 사용자가 새 사용자의 알림을 계속 받는 것을 막는 경로다.
     *
     * <p>프론트가 포그라운드 복귀마다 현재 구독을 다시 POST하는 것을 전제로 하므로, 이 메서드는 자주 호출되어도
     * 안전해야 한다.
     */
    @Transactional
    public void subscribe(UUID boormiId, PushSubscriptionRequest request, String userAgent) {
        pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing -> existing.refresh(
                                boormiId, request.keys().p256dh(), request.keys().auth(), userAgent),
                        () -> pushSubscriptionRepository.save(PushSubscription.create(
                                boormiId, request.endpoint(),
                                request.keys().p256dh(), request.keys().auth(), userAgent)));
    }

    /**
     * 구독을 해제한다(멱등). 로그아웃 시 프론트가 세션이 살아 있는 동안 호출한다.
     *
     * <p>브라우저 쪽 구독은 일부러 남긴다({@code unsubscribe()}를 부르지 않는다) — 권한이 유지되므로 재로그인 때
     * 두 번째 권한 프롬프트 없이 조용히 재등록된다. 전달을 실제로 막는 것은 여기서 지우는 이 행이라 공용 기기도 안전하다.
     */
    @Transactional
    public void unsubscribe(UUID boormiId, String endpoint) {
        pushSubscriptionRepository.findByEndpoint(endpoint)
                .filter(subscription -> subscription.isOwnedBy(boormiId))
                .ifPresent(pushSubscriptionRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<PushSubscription> findAllFor(UUID boormiId) {
        return pushSubscriptionRepository.findAllByBoormiId(boormiId);
    }

    /**
     * 전송 결과를 구독 행에 반영한다. 삭제해도 되는 결과(EXPIRED, 연속 실패 임계 초과)와 절대 삭제하면 안 되는
     * 결과(RATE_LIMITED, REJECTED)를 구분하는 것이 이 메서드의 존재 이유다.
     */
    @Transactional
    public void applyOutcome(String endpoint, PushSendOutcome outcome) {
        pushSubscriptionRepository.findByEndpoint(endpoint).ifPresent(subscription -> {
            switch (outcome) {
                case SUCCESS -> subscription.markSuccess();
                case EXPIRED -> prune(subscription, "expired");
                case RETRIABLE_FAILURE -> {
                    subscription.markFailure();
                    if (subscription.isDead()) {
                        prune(subscription, "too_many_failures");
                    }
                }
                // 레이트 리밋·설정 오류·페이로드 과대는 구독의 건강 상태와 무관하므로 행을 건드리지 않는다.
                case RATE_LIMITED, REJECTED, PAYLOAD_TOO_LARGE -> {
                }
            }
        });
    }

    private void prune(PushSubscription subscription, String reason) {
        pushSubscriptionRepository.delete(subscription);
        meterRegistry.counter("push.subscriptions.pruned", "reason", reason).increment();
        log.debug("죽은 푸시 구독 정리: boormiId={}, reason={}", subscription.getBoormiId(), reason);
    }
}
