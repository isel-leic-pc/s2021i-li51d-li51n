package pt.isel.pc.examples.synchronizers;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.examples.utils.TestHelper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;

public class NArySemaphoreTests {

    private static final int N_OF_THREADS = 100;
    private static final Duration TEST_DURATION = Duration.ofSeconds(10);
    private static final Logger log = LoggerFactory.getLogger(NArySemaphoreTests.class);

    private void does_not_exceed_max_units(NArySemaphore semaphore, int units) throws InterruptedException {
        AtomicInteger acquiredUnits = new AtomicInteger(units);
        TestHelper helper = new TestHelper(TEST_DURATION);

        helper.createAndStartMultiple(N_OF_THREADS, (ignore, isDone) -> {
            while (!isDone.get()) {
                int requestedUnits = ThreadLocalRandom.current().nextInt(units) + 1;
                semaphore.acquire(requestedUnits, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                int current = acquiredUnits.addAndGet(-requestedUnits);
                if (current < 0) {
                    throw new AssertionError("acquiredUnits is below zero");
                }
                Thread.yield();
                acquiredUnits.addAndGet(requestedUnits);
                semaphore.release(requestedUnits);
            }
        });

        helper.join();
    }

    @Test
    public void SimpleNArySempahoreV0_simple_test() throws InterruptedException {
        int units = N_OF_THREADS / 3;
        does_not_exceed_max_units(new SimpleNArySemaphoreV0(units), units);
    }

    @Test
    public void SimpleNArySempahoreV1_simple_test() throws InterruptedException {
        int units = N_OF_THREADS / 3;
        does_not_exceed_max_units(new NArySemaphoreWithFifoOrder(units), units);
    }

    @Test
    public void NArySemaphoreWithSpecificNotification_simple_test() throws InterruptedException {
        int units = N_OF_THREADS / 3;
        does_not_exceed_max_units(new NArySemaphoreWithSpecificNotification(units), units);
    }

    @Test
    public void NArySemaphoreUsingKernelStyle_simple_test() throws InterruptedException {
        int units = N_OF_THREADS / 3;
        does_not_exceed_max_units(new NArySemaphoreUsingKernelStyle(units), units);
    }

    private void order_test(NArySemaphore sem) throws InterruptedException {
        final ConcurrentLinkedQueue<Long> acquiredUnits = new ConcurrentLinkedQueue<>();
        TestHelper helper = new TestHelper(TEST_DURATION);
        helper.createAndStartMultiple(N_OF_THREADS, (ignore, isDone) -> {
            long totalAcquires = 0;
            while (!isDone.get() && totalAcquires < Long.MAX_VALUE) {
                sem.acquire(1, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                totalAcquires += 1;
                sem.release(1);
            }
            acquiredUnits.add(totalAcquires);
        });
        helper.join();
        long max = Collections.max(acquiredUnits);
        long min = Collections.min(acquiredUnits);
        log.info("min acquired units = {}, max acquired units = {}, diff = {}",
                max, min, max - min);
        if (max - min > 0.01 * min) {
            throw new AssertionError("acquired units (max-min) exceeds 1% of min");
        }
    }

    @Test
    public void NArySemaphoreWithFifoOrder_order_test() throws InterruptedException {
        order_test(new NArySemaphoreWithFifoOrder(1));
    }

    @Test
    public void NArySemaphoreWithSpecificNotification_order_test() throws InterruptedException {
        order_test(new NArySemaphoreWithSpecificNotification(1));
    }

    @Test
    public void NArySemaphoreUsingKernelStyle_order_test() throws InterruptedException {
        order_test(new NArySemaphoreUsingKernelStyle(1));
    }

    private void order_test2(Function<Integer, NArySemaphore> create) throws InterruptedException {
        NArySemaphore semaphore = create.apply(4);
        TestHelper helper = new TestHelper(TEST_DURATION);
        Duration waitDuration = Duration.ofMillis(100);
        ConcurrentHashMap<Integer, Long> counts = new ConcurrentHashMap<>();

        helper.createAndStartMultiple(2, (index, isDone) -> {
            long count = 0;
            while (!isDone.get()) {
                semaphore.acquire(2, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                count += 1;
                Thread.sleep(waitDuration.toMillis());
                semaphore.release(2);
            }
            counts.put(index, count);
        });
        helper.createAndStartMultiple(1, (index, isDone) -> {
            long count = 0;
            while (!isDone.get()) {
                semaphore.acquire(3, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                count += 1;
                Thread.sleep(waitDuration.toMillis());
                semaphore.release(3);
            }
            counts.put(2, count);
        });
        helper.join();
        List<Long> results = Arrays.asList(counts.get(0), counts.get(1), counts.get(2));
        long targetCount = TEST_DURATION.dividedBy(waitDuration)/2;
        log.info("targetCount = {}, counts = {}", targetCount, results);
        results.forEach(count -> {
            assertTrue(Math.abs(targetCount - count) <= 1);
        });
        long min = Collections.min(results);
        long max = Collections.max(results);
        assertTrue(max <= min + 2);
    }

    @Test
    public void NArySemaphoreWithFifoOrder_order_test2() throws InterruptedException {
        order_test2(NArySemaphoreWithFifoOrder::new);
    }

    @Test
    public void NArySemaphoreWithSpecificNotification_order_test2() throws InterruptedException {
        order_test2(NArySemaphoreWithFifoOrder::new);
    }

    @Test
    public void NArySemaphoreUsingKernelStyle_order_test2() throws InterruptedException {
        order_test2(NArySemaphoreWithFifoOrder::new);
    }

}
