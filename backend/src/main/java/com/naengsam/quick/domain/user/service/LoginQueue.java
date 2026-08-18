package com.naengsam.quick.domain.user.service;

import com.naengsam.quick.domain.user.dto.LoginCredential;
import com.naengsam.quick.domain.user.dto.LoginRequest;
import com.naengsam.quick.domain.user.dto.LoginResultDto;
import com.naengsam.quick.domain.user.exception.AuthErrorCode;
import com.naengsam.quick.global.admin.InMemoryStateProbe;
import com.naengsam.quick.global.admin.InMemoryStructureDto;
import com.naengsam.quick.global.exception.BusinessException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로그인 해싱을 {@code login.queue.permits}개로 묶고, 넘치는 요청은 대기열에 세운다.
 *
 * <p><b>이 대기열은 처리량을 올리지 못한다.</b> PBKDF2 210,000회는 CPU 바운드라 천장이
 * {@code vCPU 수 / 해시 시간}으로 고정돼 있다. 대기열이 파는 것은 세 가지다.
 * <ol>
 *   <li>격리 — 로그인 폭주가 톰캣 스레드를 전부 잡아먹어 나머지 API 를 굶기지 않게 한다</li>
 *   <li>예측 가능성 — 사용자가 자기 순번과 예상 대기 시간을 본다</li>
 *   <li>정상 실패 — DB 커넥션 타임아웃 500 대신 명시적 대기 또는 503 으로 떨어진다</li>
 * </ol>
 *
 * <p><b>동시 해싱 개수는 {@link #hashPermits} 하나로만 정해진다.</b> 즉시 처리 경로(요청 스레드)와
 * 워커 스레드가 같은 세마포어를 쓰므로, 두 경로를 합쳐도 permits 를 넘지 않는다.
 *
 * <p><b>평문 비밀번호는 Redis 에 넣지 않는다.</b> Redis 는 TLS 미구성이고, 공유 저장소에 자격증명을 두는 것은
 * 힙 대비 명백한 보안 후퇴다. Redis 에는 순번·상태·결과(boormiId 또는 에러코드)만 두고, 이메일/비밀번호는
 * {@link #pending} 에만 두었다가 워커가 꺼내는 즉시 제거한다. 어떤 경로로도 로그에 남기지 않는다.
 * (비밀번호를 {@code char[]}로 들고 다니며 소거하지는 않는다. Jackson 이 요청 본문을 이미 불변 String 으로
 * 만들어 힙에 올려두므로, 여기서만 소거해봐야 실효가 없다.)
 *
 * <p>즉시 처리 경로는 Redis 장애 대비책을 겸한다. 대기열이 비어 있으면 Redis 를 아예 건드리지 않으므로,
 * Redis 가 죽어도 저부하에서는 로그인이 그대로 된다. 고부하에서만 {@code LOGIN_QUEUE_UNAVAILABLE}(503)이 된다.
 *
 * <p>단일 인스턴스 전제다. 서버를 늘리면 순번({@code ZRANK})은 전역이지만 자격증명이 힙에 있어 해싱은
 * 티켓을 받은 노드만 수행하므로, 실제 처리 순서는 노드별 FIFO 의 인터리빙이 된다(순번은 근사값).
 */
@Slf4j
@Component
public class LoginQueue implements InMemoryStateProbe {

    private static final String QUEUE_KEY = "login:queue";
    private static final String TICKET_KEY_PREFIX = "login:ticket:";

    private static final String STATE_WAITING = "WAITING";
    private static final String STATE_READY = "READY";
    private static final String STATE_FAILED = "FAILED";

    private static final long QUEUE_FULL = -1L;

    private static final long MIN_POLL_MS = 500L;
    private static final long MAX_POLL_MS = 5_000L;
    /** 예상 대기 1초당 폴링 간격 250ms. 순번이 줄면 자동으로 촘촘해지고, 뒤쪽 대기자는 느리게 돈다. */
    private static final long POLL_MS_PER_SECOND = 250L;

    /**
     * 대기열 통과 결과. {@code boormiId}가 있으면 컨트롤러가 세션을 만들 차례이고,
     * 없으면 {@code response}를 그대로 내려보내면 된다.
     */
    public record Progress(UUID boormiId, LoginResultDto response) {
    }

    /** 해싱 전까지만 힙에 머무는 자격증명. 워커가 꺼내는 순간 맵에서 사라진다. */
    private record PendingLogin(String email, String password) {
    }

    private final StringRedisTemplate redis;
    private final RedisScript<Long> loginEnqueueScript;
    private final RedisScript<Long> loginAdmitScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> loginClaimScript;
    private final LoginQueueProperties props;
    private final UserService userService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final Semaphore hashPermits;
    private final BlockingQueue<String> localQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, PendingLogin> pending = new ConcurrentHashMap<>();
    private final Timer hashTimer;

    @SuppressWarnings("rawtypes")
    public LoginQueue(StringRedisTemplate redis,
                      RedisScript<Long> loginEnqueueScript,
                      RedisScript<Long> loginAdmitScript,
                      RedisScript<List> loginClaimScript,
                      LoginQueueProperties props,
                      UserService userService,
                      MeterRegistry meterRegistry,
                      Clock clock) {
        this.redis = redis;
        this.loginEnqueueScript = loginEnqueueScript;
        this.loginAdmitScript = loginAdmitScript;
        this.loginClaimScript = loginClaimScript;
        this.props = props;
        this.userService = userService;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.hashPermits = new Semaphore(props.permits());
        this.hashTimer = Timer.builder("login.hash")
                .description("비밀번호 해싱 1회 소요 시간. permits 와 estimated-hash-duration 을 정하는 실측 근거")
                .register(meterRegistry);
        Gauge.builder("login.queue.waiting", localQueue, BlockingQueue::size)
                .description("이 인스턴스에서 해싱을 기다리는 로그인 수")
                .register(meterRegistry);
    }

    /**
     * 해싱 슬롯이 남고 대기열도 비어 있으면 요청 스레드에서 바로 처리하고, 아니면 대기열에 등록한다.
     *
     * <p>워커가 큐에서 티켓을 꺼낸 뒤 세마포어를 기다리는 짧은 순간에는 즉시 처리 경로가 앞질러 갈 수 있다.
     * 새치기 폭은 permits 개로 묶여 있어 순번 역전이 눈에 띄지 않으므로 best-effort 로 둔다.
     */
    public Progress submit(LoginRequest request) {
        if (hashPermits.tryAcquire()) {
            try {
                if (localQueue.isEmpty()) {
                    UUID boormiId = hashTimer.record(() -> userService.login(request));
                    return new Progress(boormiId, LoginResultDto.success());
                }
            } finally {
                hashPermits.release();
            }
        }
        return new Progress(null, enqueue(request));
    }

    /**
     * 티켓 상태를 조회한다. 아직 대기 중이면 순번을, 처리가 끝났으면 결과를 돌려주고 티켓을 소비한다.
     * 로그인 실패·정지·탈퇴는 대기열을 거쳐도 같은 에러코드로 여기서 터진다.
     */
    public Progress poll(String ticketId) {
        List<?> result;
        try {
            result = redis.execute(loginClaimScript,
                    List.of(QUEUE_KEY, TICKET_KEY_PREFIX + ticketId),
                    ticketId);
        } catch (Exception e) {
            log.error("로그인 대기열 조회 실패(Redis)", e);
            meterRegistry.counter("login.queue.rejected", "reason", "unavailable").increment();
            throw new BusinessException(AuthErrorCode.LOGIN_QUEUE_UNAVAILABLE);
        }

        if (result == null || result.isEmpty()) {
            meterRegistry.counter("login.queue.rejected", "reason", "expired").increment();
            throw new BusinessException(AuthErrorCode.LOGIN_TICKET_EXPIRED);
        }

        String state = String.valueOf(result.get(0));
        String payload = String.valueOf(result.get(1));

        if (STATE_WAITING.equals(state)) {
            // 스윕으로 ZSET 멤버만 먼저 사라졌으면 순번이 0 으로 온다. 표시용으로 1 이상으로 보정한다.
            int position = Math.max(1, Integer.parseInt(String.valueOf(result.get(2))));
            int totalWaiting = Math.max(position, Integer.parseInt(String.valueOf(result.get(3))));
            int estimatedWaitSeconds = estimatedWaitSeconds(position);
            return new Progress(null, LoginResultDto.waiting(position, totalWaiting,
                    estimatedWaitSeconds, pollAfterMs(estimatedWaitSeconds)));
        }

        if (STATE_FAILED.equals(state)) {
            throw new BusinessException(toAuthErrorCode(payload));
        }

        return new Progress(UUID.fromString(payload), LoginResultDto.success());
    }

    private LoginResultDto enqueue(LoginRequest request) {
        String ticketId = UUID.randomUUID().toString();

        Long rank;
        try {
            rank = redis.execute(loginEnqueueScript,
                    List.of(QUEUE_KEY, TICKET_KEY_PREFIX + ticketId),
                    ticketId,
                    Long.toString(clock.millis()),
                    Integer.toString(props.capacity()),
                    Long.toString(props.ticketTtl().toMillis()));
        } catch (Exception e) {
            log.error("로그인 대기열 등록 실패(Redis)", e);
            meterRegistry.counter("login.queue.rejected", "reason", "unavailable").increment();
            throw new BusinessException(AuthErrorCode.LOGIN_QUEUE_UNAVAILABLE);
        }

        if (rank == null || rank == QUEUE_FULL) {
            meterRegistry.counter("login.queue.rejected", "reason", "full").increment();
            throw new BusinessException(AuthErrorCode.LOGIN_QUEUE_FULL);
        }
        // Redis 등록이 끝난 뒤에 로컬에 넣는다. 순서가 뒤집히면 워커가 아직 없는 티켓을 처리하려 든다.
        pending.put(ticketId, new PendingLogin(request.email(), request.password()));
        localQueue.add(ticketId);
        meterRegistry.counter("login.queue.enqueued").increment();

        int position = rank.intValue();
        int estimatedWaitSeconds = estimatedWaitSeconds(position);
        return LoginResultDto.queued(ticketId, position, totalWaiting(position),
                estimatedWaitSeconds, pollAfterMs(estimatedWaitSeconds));
    }

    /**
     * 워커를 permits 개 띄운다. 가상 스레드를 쓰지 않는 이유는 이 작업이 CPU 바운드라서다. 가상 스레드는
     * 블로킹 I/O 를 겹치는 데 쓰는 것이고, 여기서는 "정확히 permits 개만 동시에 돈다"가 요점이다.
     * 종료를 막지 않도록 데몬으로 띄운다(처리 중이던 티켓은 TTL 로 정리된다).
     */
    @PostConstruct
    void startWorkers() {
        for (int i = 1; i <= props.permits(); i++) {
            Thread.ofPlatform().name("login-hasher-" + i).daemon(true).start(this::run);
        }
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String ticketId = localQueue.take();
                PendingLogin login = pending.remove(ticketId);
                if (login == null) {
                    continue;
                }

                hashPermits.acquire();
                try {
                    process(ticketId, login);
                } finally {
                    hashPermits.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // 티켓 하나의 예외로 워커가 죽지 않게 막는다. 상태를 못 쓴 티켓은 TTL 로 만료된다.
                log.error("로그인 대기열 처리 실패", e);
            }
        }
    }

    private void process(String ticketId, PendingLogin login) {
        String ticketKey = TICKET_KEY_PREFIX + ticketId;
        if (Boolean.FALSE.equals(redis.hasKey(ticketKey))) {
            // 사용자가 창을 닫았거나 TTL 이 지난 티켓. 해싱 비용을 쓰지 않고 버린다.
            meterRegistry.counter("login.queue.rejected", "reason", "expired").increment();
            return;
        }

        String state;
        String payload;
        try {
            LoginCredential credential = userService.loadCredential(login.email());
            UUID boormiId = hashTimer.record(() -> userService.verify(credential, login.password()));
            state = STATE_READY;
            payload = boormiId.toString();
        } catch (BusinessException e) {
            // 로그인 실패·정지·탈퇴는 폴링 시점이 아니라 클레임 시점에 사용자에게 전달된다.
            state = STATE_FAILED;
            payload = e.getErrorCode().getCode();
        }

        redis.execute(loginAdmitScript, List.of(QUEUE_KEY, ticketKey),
                ticketId, state, payload, Long.toString(props.readyTtl().toMillis()));
        meterRegistry.counter("login.queue.admitted").increment();
    }

    /**
     * TTL 이 지난 ZSET 멤버를 걷어낸다. 티켓 HASH 는 TTL 로 알아서 사라지지만 ZSET 멤버에는 개별 TTL 이 없어,
     * 이탈한 대기자가 남아 뒷사람 순번을 계속 부풀린다.
     */
    @Scheduled(fixedDelay = 30_000L)
    void sweepExpired() {
        try {
            redis.opsForZSet().removeRangeByScore(QUEUE_KEY, Double.NEGATIVE_INFINITY,
                    clock.millis() - props.ticketTtl().toMillis());
        } catch (Exception e) {
            log.warn("로그인 대기열 스윕 실패(Redis)", e);
        }
    }

    private int totalWaiting(int fallback) {
        try {
            Long total = redis.opsForZSet().zCard(QUEUE_KEY);
            return total == null ? fallback : Math.max(fallback, total.intValue());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 앞선 순번이 permits 개씩 소화된다고 보고 남은 시간을 계산한다. */
    private int estimatedWaitSeconds(int position) {
        long millis = (long) position * props.estimatedHashDuration().toMillis() / props.permits();
        return (int) Math.max(1, (millis + 999) / 1000);
    }

    private int pollAfterMs(int estimatedWaitSeconds) {
        return (int) Math.clamp(estimatedWaitSeconds * POLL_MS_PER_SECOND, MIN_POLL_MS, MAX_POLL_MS);
    }

    private static AuthErrorCode toAuthErrorCode(String code) {
        for (AuthErrorCode value : AuthErrorCode.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return AuthErrorCode.LOGIN_FAILED;
    }

    /**
     * 해싱을 기다리는 티켓 현황. 워커가 꺼내는 즉시 두 자료구조에서 모두 빠지므로 정상 상태에서는 0에 가깝고, 여기가 계속 남아 있으면 워커 스레드가 멈췄다는 뜻이다.
     *
     * <p>{@code pending}의 값은 평문 이메일·비밀번호이므로 값은 어떤 형태로도 내보내지 않는다. 샘플로 내보내는 것은 서버가 발급한 ticketId 뿐이다.
     */
    @Override
    public List<InMemoryStructureDto> inMemoryStructures() {
        return List.of(
                InMemoryStructureDto.ofCollection("localQueue", "해싱 대기 중인 ticketId FIFO", localQueue),
                InMemoryStructureDto.ofMap("pending", "ticketId → 해싱 전 자격증명 (값은 미노출)", pending));
    }
}
