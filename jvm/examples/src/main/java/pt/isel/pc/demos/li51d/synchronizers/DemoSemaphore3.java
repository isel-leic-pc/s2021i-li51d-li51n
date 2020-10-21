package pt.isel.pc.demos.li51d.synchronizers;

import pt.isel.pc.utils.Timeouts;

public class DemoSemaphore3 {

    private int units;
    private final Object monitor = new Object();

    public DemoSemaphore3(int initialUnits) {
        this.units = initialUnits;
    }

    public boolean acquire(int requestedUnits, long timeout) throws InterruptedException {
        synchronized (monitor) {
            // fast-path
            if (units >= requestedUnits) {
                units -= requestedUnits;
                return true;
            }
            // wait-path
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            final long deadline = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(deadline);
            while (true) {
                monitor.wait(remaining);
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
        synchronized (monitor) {
            units += releasedUnits;
            monitor.notifyAll();
        }
    }
}


