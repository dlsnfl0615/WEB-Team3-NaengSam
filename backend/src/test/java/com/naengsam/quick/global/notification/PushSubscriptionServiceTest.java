package com.naengsam.quick.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.naengsam.quick.global.notification.dto.PushSubscriptionRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 웹푸시 구독 행의 upsert와 전송 결과별 정리 규칙을 검증한다. */
class PushSubscriptionServiceTest {

    private static final String ENDPOINT = "https://push.example/abc";

    private PushSubscriptionRepository pushSubscriptionRepository;
    private PushSubscriptionService pushSubscriptionService;

    private static PushSubscriptionRequest subscribeRequest() {
        return new PushSubscriptionRequest(ENDPOINT, new PushSubscriptionRequest.Keys("공개키", "인증시크릿"));
    }

    private static PushSubscription existingSubscription(UUID ownerId) {
        return PushSubscription.create(ownerId, ENDPOINT, "이전공개키", "이전인증시크릿", "이전기기");
    }

    @BeforeEach
    void setUp() {
        pushSubscriptionRepository = mock(PushSubscriptionRepository.class);
        pushSubscriptionService = new PushSubscriptionService(pushSubscriptionRepository, new SimpleMeterRegistry());
    }

    @Test
    void 처음_보는_엔드포인트면_새_구독을_저장한다() {
        UUID boormiId = UUID.randomUUID();
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.empty());

        pushSubscriptionService.subscribe(boormiId, subscribeRequest(), "Mozilla/5.0");

        verify(pushSubscriptionRepository).save(any(PushSubscription.class));
    }

    @Test
    void 같은_엔드포인트를_다시_보내면_행을_추가하지_않고_키를_갱신한다() {
        UUID boormiId = UUID.randomUUID();
        PushSubscription existing = existingSubscription(boormiId);
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(existing));

        pushSubscriptionService.subscribe(boormiId, subscribeRequest(), "Mozilla/5.0");

        assertThat(existing.getP256dh()).isEqualTo("공개키");
        assertThat(existing.getAuth()).isEqualTo("인증시크릿");
        verify(pushSubscriptionRepository, never()).save(any());
    }

    @Test
    void 공용_기기에서_계정이_바뀌면_소유자를_재배정한다() {
        UUID previousOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        PushSubscription existing = existingSubscription(previousOwner);
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(existing));

        pushSubscriptionService.subscribe(newOwner, subscribeRequest(), "Mozilla/5.0");

        // 두 번째 행이 생기면 이전 사용자가 새 사용자의 알림을 계속 받게 된다.
        assertThat(existing.getBoormiId()).isEqualTo(newOwner);
        verify(pushSubscriptionRepository, never()).save(any());
    }

    @Test
    void 남의_구독은_해제_요청으로_지워지지_않는다() {
        PushSubscription othersSubscription = existingSubscription(UUID.randomUUID());
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(othersSubscription));

        pushSubscriptionService.unsubscribe(UUID.randomUUID(), ENDPOINT);

        verify(pushSubscriptionRepository, never()).delete(any());
    }

    @Test
    void EXPIRED_결과는_구독을_즉시_삭제한다() {
        PushSubscription subscription = existingSubscription(UUID.randomUUID());
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(subscription));

        pushSubscriptionService.applyOutcome(ENDPOINT, PushSendOutcome.EXPIRED);

        verify(pushSubscriptionRepository).delete(subscription);
    }

    @Test
    void RATE_LIMITED_결과는_구독을_삭제하지도_실패로_세지도_않는다() {
        PushSubscription subscription = existingSubscription(UUID.randomUUID());
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(subscription));

        pushSubscriptionService.applyOutcome(ENDPOINT, PushSendOutcome.RATE_LIMITED);

        assertThat(subscription.getConsecutiveFailures()).isZero();
        verify(pushSubscriptionRepository, never()).delete(any());
    }

    @Test
    void VAPID_설정_오류로_거부되어도_구독을_삭제하지_않는다() {
        PushSubscription subscription = existingSubscription(UUID.randomUUID());
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(subscription));

        pushSubscriptionService.applyOutcome(ENDPOINT, PushSendOutcome.REJECTED);

        // 키를 잘못 넣은 배포 한 번에 전체 구독이 날아가면 안 된다.
        verify(pushSubscriptionRepository, never()).delete(any());
    }

    @Test
    void 연속_실패가_임계치에_이르면_구독을_정리한다() {
        PushSubscription subscription = existingSubscription(UUID.randomUUID());
        ReflectionTestUtils.setField(
                subscription, "consecutiveFailures", PushSubscription.MAX_CONSECUTIVE_FAILURES - 1);
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(subscription));

        pushSubscriptionService.applyOutcome(ENDPOINT, PushSendOutcome.RETRIABLE_FAILURE);

        verify(pushSubscriptionRepository).delete(subscription);
    }

    @Test
    void 전송에_성공하면_실패_카운터가_리셋된다() {
        PushSubscription subscription = existingSubscription(UUID.randomUUID());
        ReflectionTestUtils.setField(subscription, "consecutiveFailures", 3);
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(subscription));

        pushSubscriptionService.applyOutcome(ENDPOINT, PushSendOutcome.SUCCESS);

        assertThat(subscription.getConsecutiveFailures()).isZero();
        assertThat(subscription.getLastSuccessDtm()).isNotNull();
        verify(pushSubscriptionRepository, never()).delete(any());
    }
}
