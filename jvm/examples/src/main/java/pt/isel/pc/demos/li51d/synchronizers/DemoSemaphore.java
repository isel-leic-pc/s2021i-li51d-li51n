package pt.isel.pc.demos.li51d.synchronizers;

import pt.isel.pc.utils.Timeouts;

public class DemoSemaphore {

    private int units;
    private final Object monitor = new Object();

    public DemoSemaphore(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(long timeout) throws InterruptedException {
        synchronized (monitor) {
            // fast-path
            if (units > 0) {
                units -= 1;
                return true;
            }
            // wait-path
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            final long deadline = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                try {
                    monitor.wait(remaining);
                } catch (InterruptedException e) {
                    if (units > 0) {
                        monitor.notify();
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
        synchronized (monitor) {
            units += 1;
            monitor.notify();
        }
    }
}


