package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.utils.Timeouts;

import java.util.concurrent.TimeUnit;

/**
 * Unary semaphore without FIFO policy and using {@link Object#notifyAll} on the
 * {@link SimpleUnarySemaphoreV0#release} method. This algorithm is not efficient and can be improved,
 * since it produces unneeded context switches when there are multiple threads waiting on the condition.
 */
public class SimpleUnarySemaphoreV0 {

    // The shared semaphore units
    private int units;
    private final Object lock = new Object();

    public SimpleUnarySemaphoreV0(int units) {
        this.units = units;
    }

    public boolean acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {

        // Always observe or mutate state while holding the lock
        synchronized (lock) {
            // fast path
            if (units > 0) {
                units -= 1;
                return true;
            }
            // check if no wait should be performed
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            // compute deadline and remaining time
            long deadline = Timeouts.start(timeout, timeUnit);
            long remaining = Timeouts.remaining(deadline);

            while (true) {
                // wait
                lock.wait(remaining);

                // note that there is an quit path via the
                // InterruptedException1

                // evaluate condition
                if (units > 0) {
                    units -= 1;
                    return true;
                }
                // recompute remaining time
                remaining = Timeouts.remaining(deadline);

                // Quit if deadline reached
                if (Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }
        }
    }

    public void release() {

        // Always observe or mutate state while holding the lock
        synchronized (lock) {
            units += 1;
            // Wakeup all threads, i.e., all threads will have the opportunity to
            // observe the shared state again.
            lock.notifyAll();
        }
    }
}
