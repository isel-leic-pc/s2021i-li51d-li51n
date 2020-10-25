package pt.isel.pc.examples.synchronizers;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.examples.utils.TestHelper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class UnarySemaphoreTests {

    private static final int N_OF_THREADS = 100;
    private static final Duration TEST_DURATION = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(UnarySemaphoreTests.class);

    private void does_not_exceed_max_units(UnarySemaphore semaphore, int units) throws InterruptedException {
        AtomicInteger acquiredUnits = new AtomicInteger(units);
        TestHelper helper = new TestHelper(TEST_DURATION);

        helper.createAndStartMultiple(N_OF_THREADS, (ignore, isDone) -> {
            while (!isDone.get()) {
                semaphore.acquire(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                int current = acquiredUnits.decrementAndGet();
                if (current < 0) {
                    throw new AssertionError("acquiredUnits is below zero");
                }
                Thread.yield();
                acquiredUnits.incrementAndGet();
                semaphore.release();
            }
        });

        helper.join();
    }

    @Test
    public void SimpleUnarySempahoreV1_simple_test() throws InterruptedException {
        int units = N_OF_THREADS / 3;
        does_not_exceed_max_units(new SimpleUnarySemaphoreV1(units), units);
    }

    @Test
    public void SimpleNArySempahoreV0_simple_test() throws InterruptedException {
        int units = N_OF_THREADS / 3;
        does_not_exceed_max_units(new NArySemaphoreAdapter(new SimpleNArySemaphoreV0(units)), units);
    }

    @Test
    public void SimpleNArySempahoreV1_simple_test() throws InterruptedException {
        int units = N_OF_THREADS / 3;
        does_not_exceed_max_units(new NArySemaphoreAdapter(new NArySemaphoreWithFifoOrder(units)), units);
    }

    static class NArySemaphoreAdapter implements UnarySemaphore {

        private final NArySemaphore delegate;

        public NArySemaphoreAdapter(NArySemaphore delegate) {

            this.delegate = delegate;
        }

        @Override
        public boolean acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return delegate.acquire(1, timeout, timeUnit);
        }

        @Override
        public void release() {
            delegate.release(1);
        }
    }

}
