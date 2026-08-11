package com.naengsam.quick.domain.matching.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MatchingScheduler가 즉시/지연/반복 Action을 단일 워커 스레드에서 deadline+sequence 순서로 실행하고, 취소와 예외를 올바르게
 * 처리하는지 검증한다.
 */
class MatchingSchedulerTest {

    private MatchingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MatchingScheduler();
        scheduler.start();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void 즉시_제출한_Action이_실행된다() throws InterruptedException {
        CountDownLatch executed = new CountDownLatch(1);

        scheduler.submit(executed::countDown);

        assertThat(executed.await(500, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    void 즉시_제출한_Action들은_제출한_순서대로_실행된다() throws InterruptedException {
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            int index = i;
            scheduler.submit(() -> {
                order.add(index);
                done.countDown();
            });
        }

        assertThat(done.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(order).containsExactly(0, 1, 2);
    }

    @Test
    void 지정한_지연_후에_Action이_실행된다() throws InterruptedException {
        CountDownLatch executed = new CountDownLatch(1);

        scheduler.schedule(executed::countDown, Duration.ofMillis(100));

        assertThat(executed.await(50, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(executed.await(500, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    void 실행_시각이_더_빠른_Action이_먼저_실행된다() throws InterruptedException {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(2);

        scheduler.schedule(() -> {
            order.add("slow");
            done.countDown();
        }, Duration.ofMillis(150));
        scheduler.schedule(() -> {
            order.add("fast");
            done.countDown();
        }, Duration.ofMillis(30));

        assertThat(done.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(order).containsExactly("fast", "slow");
    }

    @Test
    void 실행_시각이_같으면_제출한_순서로_실행된다() throws InterruptedException {
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            int index = i;
            scheduler.schedule(() -> {
                order.add(index);
                done.countDown();
            }, Duration.ofMillis(50));
        }

        assertThat(done.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(order).containsExactly(0, 1, 2);
    }

    @Test
    void 하나의_Action이_예외를_던져도_워커_스레드는_죽지_않고_다음_Action을_계속_처리한다() throws InterruptedException {
        CountDownLatch nextExecuted = new CountDownLatch(1);

        scheduler.submit(() -> {
            throw new RuntimeException("boom");
        });
        scheduler.submit(nextExecuted::countDown);

        assertThat(nextExecuted.await(500, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    void 취소된_지연_Action은_실행되지_않는다() throws InterruptedException {
        AtomicInteger executedCount = new AtomicInteger();
        ScheduledActionHandle handle = scheduler.schedule(executedCount::incrementAndGet, Duration.ofMillis(100));

        handle.cancel();

        Thread.sleep(300);
        assertThat(executedCount.get()).isZero();
    }

    @Test
    void 취소된_반복_Action은_취소_이후_회차가_실행되지_않는다() throws InterruptedException {
        AtomicInteger executedCount = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        ScheduledActionHandle handle = scheduler.scheduleRepeating(() -> {
            executedCount.incrementAndGet();
            firstRun.countDown();
        }, Duration.ofMillis(30));

        assertThat(firstRun.await(500, TimeUnit.MILLISECONDS)).isTrue();
        handle.cancel();
        int countAtCancel = executedCount.get();

        Thread.sleep(200);
        assertThat(executedCount.get()).isEqualTo(countAtCancel);
    }

    @Test
    void 모든_Action은_동일한_워커_스레드에서_실행된다() throws InterruptedException {
        List<Thread> executionThreads = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(2);

        scheduler.submit(() -> {
            executionThreads.add(Thread.currentThread());
            done.countDown();
        });
        scheduler.schedule(() -> {
            executionThreads.add(Thread.currentThread());
            done.countDown();
        }, Duration.ofMillis(30));

        assertThat(done.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(executionThreads).hasSize(2);
        assertThat(executionThreads.get(0)).isSameAs(executionThreads.get(1));
    }
}
