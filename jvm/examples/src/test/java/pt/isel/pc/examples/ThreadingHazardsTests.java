package pt.isel.pc.examples;

import org.junit.Test;
import pt.isel.pc.examples.utils.TestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ThreadingHazardsTests {

    private static final int N_OF_REPS = 50000;
    private static final int N_OF_THREADS = 3;

    private int simpleCounter = 0;

    @Test
    public void loosing_increments() {

        List<Thread> ths = new ArrayList<>(N_OF_THREADS);
        for (int i = 0; i < N_OF_THREADS; ++i) {
            Thread th = new Thread(() -> {
                for (int j = 0; j < N_OF_REPS; ++j) {
                    ++simpleCounter;
                }
            });
            th.start();
            ths.add(th);
        }

        ths.forEach(TestUtils::uninterruptibleJoin);

        // notice that the assertion is NOT equals
        assertNotEquals(N_OF_THREADS * N_OF_REPS, simpleCounter);
    }

    private volatile int volatileCounter = 0;

    @Test
    public void loosing_increments_even_with_volatile() {

        List<Thread> ths = new ArrayList<>(N_OF_THREADS);
        for (int i = 0; i < N_OF_THREADS; ++i) {
            Thread th = new Thread(() -> {
                for (int j = 0; j < N_OF_REPS; ++j) {
                    ++volatileCounter;
                }
            });
            th.start();
            ths.add(th);
        }

        ths.forEach(TestUtils::uninterruptibleJoin);

        // notice that the assertion is NOT equals
        assertNotEquals(N_OF_THREADS * N_OF_REPS, volatileCounter);
    }

    private final AtomicInteger atomicCounter = new AtomicInteger();

    @Test
    public void not_loosing_increments_with_atomic() {

        List<Thread> ths = new ArrayList<>(N_OF_THREADS);
        for (int i = 0; i < N_OF_THREADS; ++i) {
            Thread th = new Thread(() -> {
                for (int j = 0; j < N_OF_REPS; ++j) {
                    atomicCounter.incrementAndGet();
                }
            });
            th.start();
            ths.add(th);
        }

        ths.forEach(TestUtils::uninterruptibleJoin);

        // notice that the assertion is NOT equals
        assertEquals(N_OF_THREADS * N_OF_REPS, atomicCounter.get());
    }

    private final Map<Integer, AtomicInteger> map = Collections.synchronizedMap(new HashMap<>());

    @Test
    public void loosing_increments_with_a_synchronized_map_and_atomics() {

        List<Thread> ths = new ArrayList<>(N_OF_THREADS);
        for (int i = 0; i < N_OF_THREADS; ++i) {
            Thread th = new Thread(() -> {
                for (int j = 0; j < N_OF_REPS; ++j) {
                    int key = j;
                    AtomicInteger data = map.get(key);
                    if(data == null) {
                        data = new AtomicInteger(1);
                        map.put(key, data);
                    }else{
                        data.incrementAndGet();
                    }
                }
            });
            th.start();
            ths.add(th);
        }

        ths.forEach(TestUtils::uninterruptibleJoin);

        // notice that the assertion is NOT equals
        int totalCount = map.values().stream()
            .map(AtomicInteger::get)
            .reduce(0, Integer::sum);

        // notice that the assertion is NOT equals
        assertNotEquals(N_OF_THREADS * N_OF_REPS, totalCount);
    }

}
