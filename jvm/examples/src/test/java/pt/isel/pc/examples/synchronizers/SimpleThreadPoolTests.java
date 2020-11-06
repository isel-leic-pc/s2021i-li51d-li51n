package pt.isel.pc.examples.synchronizers;

import org.junit.Test;
import pt.isel.pc.demos.li51n.synchronizers.SimpleThreadPool;
import pt.isel.pc.examples.utils.TestHelper;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class SimpleThreadPoolTests {

    private static final int N_OF_THREADS = 50;
    private static final Duration TEST_DURATION = Duration.ofSeconds(5);

    @Test
    public void test() throws InterruptedException {

        TestHelper testHelper = new TestHelper(TEST_DURATION);
        AtomicInteger counter = new AtomicInteger();
        SimpleThreadPool pool = new SimpleThreadPool(N_OF_THREADS/2);
        testHelper.createAndStartMultiple(N_OF_THREADS, (ix, isDone) -> {
            Random random = new Random();
            for(int i = 0; !isDone.get(); ++i) {
                int ri = random.nextInt();
                counter.addAndGet(ri);
                pool.execute(() -> counter.addAndGet(-ri));
                if(i % 100 == 0) {
                    Thread.sleep(50);
                }
            }
        });

        testHelper.join();
        // 1. pool.shutdown();
        // 2. pool.awaitTermination();
        // Never do this, add a proper synchronization method to the pool!
        while(pool.getRunningWorkers() != 0) {
            Thread.sleep(100);
        }
        assertEquals(0, counter.get());
    }

}
