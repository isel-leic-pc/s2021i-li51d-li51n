package pt.isel.pc.demos.li51n.synchronizers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OptimizedSemaphore {

    private final Lock monitor = new ReentrantLock();
    private final Condition condition = monitor.newCondition();
    private final AtomicInteger units;
    private volatile int waiters;

    public OptimizedSemaphore(int initialUnits) {
        units = new AtomicInteger(initialUnits);
    }

    public void acquire() throws InterruptedException {

        if (tryDecrement()) {
            return;
        }
        monitor.lock();
        try {
            waiters += 1;
            while (!tryDecrement()) {
                try {
                    condition.await();
                }catch(InterruptedException e) {
                    if(units.get() > 0) {
                        condition.signal();
                    }
                    throw e;
                }
            }

        } finally {
            waiters -= 1;
            monitor.unlock();
        }
    }

    public void release() {

        units.incrementAndGet();
        if (waiters == 0) {
            return;
        }
        monitor.lock();
        try {
            if(waiters > 0) {
                condition.signal();
            }
        } finally {
            monitor.unlock();
        }
    }

    private boolean tryDecrement() {
        int observedUnits;
        do {
            observedUnits = units.get();
            if (observedUnits == 0) {
                return false;
            }
        } while (!units.compareAndSet(observedUnits, observedUnits - 1));
        return true;
    }
}
