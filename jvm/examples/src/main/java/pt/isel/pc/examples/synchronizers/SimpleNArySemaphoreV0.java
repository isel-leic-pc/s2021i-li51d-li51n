package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;

/**
 * N-ary semaphore (i.e. allowing acquisition and release of multiple units) without FIFO policy a
 * and using {@link Object#notifyAll} on the
 * {@link SimpleNArySemaphoreV0#release} method. This algorithm is not efficient and can be improved,
 * since it produces unneeded context switches when there are multiple threads waiting on the condition.
 */
public class SimpleNArySemaphoreV0 implements NArySemaphore{

    private int units;
    private final Object lock = new Object();

    public SimpleNArySemaphoreV0(int units) {
        this.units = units;
    }

    public boolean acquire(int requestedUnits, long timeout, TimeUnit timeUnit) throws InterruptedException {

        synchronized (lock) {
            if (units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                lock.wait(remaining);
                if (units >= requestedUnits) {
                    units -= requestedUnits;
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }
        }
    }

    public void release(int releasedUnits) {
        synchronized (lock) {
            units += releasedUnits;
            lock.notifyAll();
        }
    }

}
