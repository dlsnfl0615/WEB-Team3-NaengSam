package com.naengsam.quick.domain.matching.service.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingEngine이 즉시/지연/반복 Action을 하나의 DelayQueue와 단일 워커 스레드로 올바르게 실행하는지 검증한다. 실제 지연은 짧은 밀리초
 * 단위로 주입해 테스트가 빠르게 끝나도록 한다.
 */
class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
        engine.start();
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    void 즉시_제출한_Action이_실행된다() {
        AtomicInteger executed = new AtomicInteger();

        engine.submit(executed::incrementAndGet);

        awaitUntil(() -> executed.get() == 1, Duration.ofSeconds(1));
    }

    @Test
    void 즉시_제출한_여러_Action은_제출_순서대로_실행된다() {
        List<Integer> executionOrder = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 20; i++) {
            int index = i;
            engine.submit(() -> executionOrder.add(index));
        }

        awaitUntil(() -> executionOrder.size() == 20, Duration.ofSeconds(1));
        assertThat(executionOrder).containsExactlyElementsOf(IntStream.range(0, 20).boxed().toList());
    }

    @Test
    void 지연_시간이_지나기_전에는_실행되지_않는다() {
        AtomicInteger executed = new AtomicInteger();

        engine.schedule(executed::incrementAndGet, Duration.ofMillis(300));

        sleep(Duration.ofMillis(100));
        assertThat(executed.get()).isZero();
    }

    @Test
    void 지연_시간이_지나면_실행된다() {
        AtomicInteger executed = new AtomicInteger();

        engine.schedule(executed::incrementAndGet, Duration.ofMillis(100));

        awaitUntil(() -> executed.get() == 1, Duration.ofSeconds(1));
    }

    @Test
    void 반복_예약된_Action은_여러_번_실행된다() {
        AtomicInteger executed = new AtomicInteger();

        engine.scheduleRepeating(executed::incrementAndGet, Duration.ofMillis(30));

        awaitUntil(() -> executed.get() >= 3, Duration.ofSeconds(1));
    }

    @Test
    void 한_번만_예약된_Action은_실행된_후_다시_실행되지_않는다() {
        AtomicInteger executed = new AtomicInteger();

        engine.schedule(executed::incrementAndGet, Duration.ZERO);

        awaitUntil(() -> executed.get() == 1, Duration.ofSeconds(1));
        sleep(Duration.ofMillis(200));
        assertThat(executed.get()).isEqualTo(1);
    }

    @Test
    void 하나의_Action이_예외를_던져도_워커_스레드는_죽지_않고_다음_Action을_계속_처리한다() {
        AtomicInteger succeeded = new AtomicInteger();

        engine.submit(() -> {
            throw new RuntimeException("boom");
        });
        engine.submit(succeeded::incrementAndGet);

        awaitUntil(() -> succeeded.get() == 1, Duration.ofSeconds(1));
    }

    @Test
    void 반복_Action이_예외를_던져도_다음_회차가_계속_예약된다() {
        AtomicInteger executed = new AtomicInteger();

        engine.scheduleRepeating(() -> {
            executed.incrementAndGet();
            throw new RuntimeException("boom");
        }, Duration.ofMillis(30));

        awaitUntil(() -> executed.get() >= 3, Duration.ofSeconds(1));
    }

    @Test
    void 음수_delay는_거부된다() {
        Throwable thrown = catchThrowable(() -> engine.schedule(() -> {
        }, Duration.ofMillis(-1)));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 반복_주기가_0_이하이면_거부된다() {
        Throwable zeroInterval = catchThrowable(() -> engine.scheduleRepeating(() -> {
        }, Duration.ZERO));
        Throwable negativeInterval = catchThrowable(() -> engine.scheduleRepeating(() -> {
        }, Duration.ofMillis(-1)));

        assertThat(zeroInterval).isInstanceOf(IllegalArgumentException.class);
        assertThat(negativeInterval).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shutdown_이후에는_제출된_Action이_실행되지_않는다() {
        AtomicInteger executed = new AtomicInteger();
        engine.shutdown();

        engine.submit(executed::incrementAndGet);

        sleep(Duration.ofMillis(200));
        assertThat(executed.get()).isZero();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("대기 중 인터럽트되었습니다.");
        }
    }

    private void awaitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("대기 중 인터럽트되었습니다.");
            }
        }
        fail("조건이 제한 시간 내에 충족되지 않았습니다.");
    }
}
