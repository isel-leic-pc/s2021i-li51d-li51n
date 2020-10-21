package pt.isel.pc.demos.li51n.synchronizers;

import pt.isel.pc.utils.Timeouts;

public class DemoSemaphore2 {

    private int units;
    private final Object monitor = new Object();

    public DemoSemaphore2(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(long timeoutInMs) throws InterruptedException {

        synchronized (monitor) {
            // fast-path
            if (units > 0) {
                units -= 1;
                return true;
            }

            if (Timeouts.noWait(timeoutInMs)) {
                return false;
            }

            final long deadline = Timeouts.start(timeoutInMs);
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
                if(units > 0) {
                    units -= 1;
                    return true;
                }
                remaining = Timeouts.remaining(deadline);
                if(Timeouts.isTimeout(remaining)) {
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
// T1 notifies T0,
// T1 exits lock,
// T2 acquires lock, ...,
// T2 exits lock,
// T0 acquires lock


