package com.naengsam.quick.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.LoginResultDto;
import com.naengsam.quick.domain.user.dto.LoginStatus;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.code.BaseErrorCode;
import com.naengsam.quick.global.exception.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 로그인 대기열의 분기(즉시 처리 / 대기 등록 / 폴링 / 클레임)와 거부 사유별 에러코드를 검증한다.
 * 워커 스레드는 띄우지 않으므로(startWorkers 미호출) 대기열에 들어간 티켓은 그대로 남아 있다.
 */
class LoginQueueTest {

    private static final int PERMITS = 1;
    private static final long HASH_MILLIS = 150L;

    private StringRedisTemplate redis;
    private RedisScript<Long> enqueueScript;
    private RedisScript<Long> admitScript;
    @SuppressWarnings("rawtypes")
    private RedisScript<List> claimScript;
    private UserService userService;
    private LoginQueue loginQueue;

    private ExecutorService executor;
    private CountDownLatch release;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        enqueueScript = mock(RedisScript.class);
        admitScript = mock(RedisScript.class);
        claimScript = mock(RedisScript.class);
        userService = mock(UserService.class);
        executor = Executors.newSingleThreadExecutor();
        release = new CountDownLatch(1);

        loginQueue = new LoginQueue(redis, enqueueScript, admitScript, claimScript, properties(), userService,
                new SimpleMeterRegistry(), Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        executor.shutdownNow();
    }

    @Test
    void 대기열이_비고_해싱_슬롯이_남으면_티켓_없이_바로_로그인된다() {
        UUID boormiId = UUID.randomUUID();
        given(userService.login(any())).willReturn(boormiId);

        LoginQueue.Progress progress = loginQueue.submit(loginRequest());

        assertThat(progress.boormiId()).isEqualTo(boormiId);
        assertThat(progress.response().status()).isEqualTo(LoginStatus.SUCCESS);
    }

    @Test
    void 대기열이_비어_있으면_Redis가_죽어도_로그인된다() {
        given(userService.login(any())).willReturn(UUID.randomUUID());

        loginQueue.submit(loginRequest());

        // 즉시 처리 경로는 Redis 를 아예 건드리지 않는다. 이것이 Redis 장애 시의 폴백이다.
        verifyNoInteractions(redis);
    }

    @Test
    void 해싱_슬롯이_없으면_대기_티켓과_순번을_돌려준다() throws Exception {
        occupyPermit();
        givenEnqueueReturns(3L);
        givenTotalWaiting(10L);

        LoginResultDto result = loginQueue.submit(loginRequest()).response();

        assertThat(result.status()).isEqualTo(LoginStatus.QUEUED);
        assertThat(result.ticketId()).isNotBlank();
        assertThat(result.position()).isEqualTo(3);
        assertThat(result.totalWaiting()).isEqualTo(10);
    }

    @Test
    void 대기열이_만석이면_LOGIN_QUEUE_FULL_예외() throws Exception {
        occupyPermit();
        givenEnqueueReturns(-1L);

        Throwable thrown = catchThrowable(() -> loginQueue.submit(loginRequest()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.LOGIN_QUEUE_FULL);
    }

    @Test
    void 등록_중_Redis가_응답하지_않으면_LOGIN_QUEUE_UNAVAILABLE_예외() throws Exception {
        occupyPermit();
        given(enqueueCall()).willThrow(new IllegalStateException("redis down"));

        Throwable thrown = catchThrowable(() -> loginQueue.submit(loginRequest()));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.LOGIN_QUEUE_UNAVAILABLE);
    }

    @Test
    void 대기_중_폴링하면_순번과_다음_폴링_간격을_돌려준다() {
        givenClaimReturns("WAITING", "", "100", "500");

        LoginResultDto result = loginQueue.poll("ticket-1").response();

        assertThat(result.status()).isEqualTo(LoginStatus.WAITING);
        assertThat(result.position()).isEqualTo(100);
        assertThat(result.totalWaiting()).isEqualTo(500);
        // permits=1, 해시 150ms 기준 100번째는 15초 대기 → 15 * 250ms
        assertThat(result.estimatedWaitSeconds()).isEqualTo(15);
        assertThat(result.pollAfterMs()).isEqualTo(3_750);
    }

    @Test
    void 순번이_앞당겨질수록_폴링_간격이_짧아진다() {
        givenClaimReturns("WAITING", "", "500", "500");
        int backOfQueue = loginQueue.poll("ticket-1").response().pollAfterMs();

        givenClaimReturns("WAITING", "", "20", "500");
        int frontOfQueue = loginQueue.poll("ticket-1").response().pollAfterMs();

        assertThat(frontOfQueue).isLessThan(backOfQueue);
        assertThat(backOfQueue).isEqualTo(5_000);
        assertThat(frontOfQueue).isEqualTo(750);
    }

    @Test
    void 차례가_되면_폴링_응답이_로그인_성공이_된다() {
        UUID boormiId = UUID.randomUUID();
        givenClaimReturns("READY", boormiId.toString(), "0", "0");

        LoginQueue.Progress progress = loginQueue.poll("ticket-1");

        assertThat(progress.boormiId()).isEqualTo(boormiId);
        assertThat(progress.response().status()).isEqualTo(LoginStatus.SUCCESS);
    }

    @Test
    void 비밀번호가_틀리면_폴링이_아니라_클레임_시점에_LOGIN_FAILED_예외() {
        givenClaimReturns("FAILED", AuthErrorCode.LOGIN_FAILED.getCode(), "0", "0");

        Throwable thrown = catchThrowable(() -> loginQueue.poll("ticket-1"));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    void 정지된_계정은_대기열을_거쳐도_SUSPENDED_ACCOUNT_예외() {
        givenClaimReturns("FAILED", AuthErrorCode.SUSPENDED_ACCOUNT.getCode(), "0", "0");

        Throwable thrown = catchThrowable(() -> loginQueue.poll("ticket-1"));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.SUSPENDED_ACCOUNT);
    }

    @Test
    void 탈퇴한_계정은_대기열을_거쳐도_WITHDRAWN_ACCOUNT_예외() {
        givenClaimReturns("FAILED", AuthErrorCode.WITHDRAWN_ACCOUNT.getCode(), "0", "0");

        Throwable thrown = catchThrowable(() -> loginQueue.poll("ticket-1"));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);
    }

    @Test
    void 만료된_티켓으로_폴링하면_LOGIN_TICKET_EXPIRED_예외() {
        given(redis.execute(same(claimScript), anyList(), any())).willReturn(List.of());

        Throwable thrown = catchThrowable(() -> loginQueue.poll("ticket-1"));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.LOGIN_TICKET_EXPIRED);
    }

    @Test
    void 폴링_중_Redis가_응답하지_않으면_LOGIN_QUEUE_UNAVAILABLE_예외() {
        given(redis.execute(same(claimScript), anyList(), any()))
                .willThrow(new IllegalStateException("redis down"));

        Throwable thrown = catchThrowable(() -> loginQueue.poll("ticket-1"));

        assertThat(errorCodeOf(thrown)).isEqualTo(AuthErrorCode.LOGIN_QUEUE_UNAVAILABLE);
    }

    /**
     * 하나뿐인 해싱 슬롯을 백그라운드 로그인이 붙잡게 해 다음 요청이 대기열로 가도록 만든다.
     * 슬롯은 {@code tearDown} 의 {@code release} 로 풀린다.
     */
    private void occupyPermit() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        given(userService.login(any())).willAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return UUID.randomUUID();
        });
        executor.submit(() -> loginQueue.submit(loginRequest()));

        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private void givenEnqueueReturns(long rank) {
        given(enqueueCall()).willReturn(rank);
    }

    /** enqueue 스크립트는 인자가 4개다. Mockito 는 varargs 를 개수까지 맞춰야 스텁이 걸린다. */
    private Long enqueueCall() {
        return redis.execute(same(enqueueScript), anyList(),
                anyString(), anyString(), anyString(), anyString());
    }

    private void givenTotalWaiting(long total) {
        ZSetOperations<String, String> zSetOperations = mockZSetOperations();
        given(redis.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.zCard(anyString())).willReturn(total);
    }

    private void givenClaimReturns(String state, String payload, String position, String totalWaiting) {
        given(redis.execute(same(claimScript), anyList(), any()))
                .willReturn(List.of(state, payload, position, totalWaiting));
    }

    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> mockZSetOperations() {
        return mock(ZSetOperations.class);
    }

    private LoginQueueProperties properties() {
        return new LoginQueueProperties(PERMITS, 500, Duration.ofMinutes(2), Duration.ofSeconds(30),
                Duration.ofMillis(HASH_MILLIS));
    }

    private LoginRequest loginRequest() {
        return new LoginRequest("user@test.com", "password1");
    }

    private BaseErrorCode errorCodeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode();
    }
}
