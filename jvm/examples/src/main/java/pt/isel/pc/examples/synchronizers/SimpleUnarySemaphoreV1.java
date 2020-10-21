package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;

/**
 * Unary semaphore without FIFO policy and using {@link Object#notify} on the
 * {@link SimpleUnarySemaphoreV0#release} method. This algorithm is more efficient than the one used for
 * {@link SimpleUnarySemaphoreV1}, since it only notifies one thread for each added unit.
 * However it does create an issue with give-up by interruption: we need to make sure the single notification is not lost.
 * This is ensured by <a href="https://docs.oracle.com/javase/specs/jls/se7/html/jls-17.html#jls-17.2.4">JLS</a>.
 */
public class SimpleUnarySemaphoreV1 {

    private int units;
    private final Object lock = new Object();

    public SimpleUnarySemaphoreV1(int units) {
        this.units = units;
    }

    public boolean acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {

        synchronized (lock) {
            if (units > 0) {
                units -= 1;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    if (units > 0) {
                        lock.notify();
                    }
                    throw e;
                }
                if (units > 0) {
                    units -= 1;
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }
        }
    }

    public void release() {
        synchronized (lock) {
            units += 1;
            lock.notify();
        }
    }

}
